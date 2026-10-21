package app.importer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Reads an SLSP workbook (the layout written by SlspExcelExporter / the accountant's monthly
 * workbook) into plain records. Columns are found by their header NAME, not their position,
 * so hidden, reordered or extra columns are fine. Only these values are taken:
 *   Sale     : date, sales invoice no., VAT exempt, zero-rated, total amount (gross)
 *   Purchase : date, invoice no., VAT exempt, zero-rated, gross, plus the supplier
 *              (TIN, trade name, address)
 * Derived columns (VAT Purchases, Input Tax, Output Tax...) are ignored; the app recomputes them.
 * Not thread-safe: use one instance per import.
 */
public final class SlspExcelImporter {

    // ---------------------------------------------------------
    // RESULT TYPES
    // ---------------------------------------------------------

    public record ParsedSale(int excelRow, LocalDate date, String invoiceNo,
                             BigDecimal exempt, BigDecimal zeroRated, BigDecimal gross) {}

    public record ParsedPurchase(int excelRow, LocalDate date, String invoiceNo,
                                 String tin, String tradeName, String address,
                                 BigDecimal exempt, BigDecimal zeroRated, BigDecimal gross) {}

    public record ParsedSupplier(String tin, String tradeName, String address) {}

    /** Everything found for one month (a month can come from a Sales sheet and a Purchases sheet). */
    public record ParsedMonth(YearMonth period, List<ParsedSale> sales, List<ParsedPurchase> purchases) {}

    public record Result(List<ParsedMonth> months, List<ParsedSupplier> suppliers, List<String> warnings) {}

    // ---------------------------------------------------------
    // HEADER NAMES (normalised: lower case, letters and digits only)
    // ---------------------------------------------------------

    private static final Set<String> DATE = Set.of("date");
    private static final Set<String> INVOICE = Set.of("salesinvoiceno", "invoiceno", "sino",
            "invoicenumber", "salesinvoice", "invoice", "siinvoiceno");
    private static final Set<String> TIN = Set.of("tin", "tinnumber", "tinno");
    private static final Set<String> NAME = Set.of("tradename", "suppliername", "supplier");
    private static final Set<String> ADDRESS = Set.of("address", "businessaddress", "bussaddress");
    private static final Set<String> EXEMPT = Set.of("vatexempt", "exempt", "exemptamount");
    private static final Set<String> ZERO = Set.of("zerorated", "zeroratedamount", "vatzerorated");
    private static final Set<String> SALES_GROSS = Set.of("totalamount", "grossamount",
            "totalamountofsales", "totalsales");
    private static final Set<String> PURCHASE_GROSS = Set.of("totalamountofpurchases", "grossamount",
            "totalpurchases");
    private static final Set<String> PURCHASE_VAT = Set.of("totalamountofvat");

    private static final Pattern TITLE = Pattern.compile(
            "(?i)VAT\\s+(SALES|PURCHASES).*?FOR\\s+THE\\s+MONTH\\s+OF\\s+([A-Za-z]+)\\s*,?\\s*(\\d{4})");
    private static final Pattern SHEET_NAME = Pattern.compile("(?i)^\\s*([A-Za-z]{3})[A-Za-z]*\\s+(\\d{4})");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH));

    private static final int HEADER_SEARCH_ROWS = 30;

    private final DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
    private FormulaEvaluator evaluator;

    // ---------------------------------------------------------
    // ENTRY POINT
    // ---------------------------------------------------------

    public Result read(InputStream in) throws IOException {
        List<String> warnings = new ArrayList<>();
        Map<YearMonth, ParsedMonth> months = new TreeMap<>();
        Map<String, ParsedSupplier> suppliers = new LinkedHashMap<>();

        try (Workbook wb = WorkbookFactory.create(in)) {
            evaluator = wb.getCreationHelper().createFormulaEvaluator();

            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                readSheet(wb.getSheetAt(i), months, suppliers, warnings);
            }
        }

        if (months.isEmpty()) {
            warnings.add("No SLSP sheets were recognised in this workbook.");
        }
        return new Result(new ArrayList<>(months.values()), new ArrayList<>(suppliers.values()), warnings);
    }

    // ---------------------------------------------------------
    // ONE SHEET
    // ---------------------------------------------------------

    private void readSheet(Sheet sheet, Map<YearMonth, ParsedMonth> months,
                           Map<String, ParsedSupplier> suppliers, List<String> warnings) {
        String sheetName = sheet.getSheetName();

        int headerRow = findHeaderRow(sheet);
        if (headerRow < 0) {
            warnings.add("Sheet \"" + sheetName + "\" skipped: no header row with a \"Date\" column.");
            return;
        }

        Map<String, Integer> cols = headerColumns(sheet.getRow(headerRow));

        // kind + period from the title ("VAT SALES FOR THE MONTH OF JUNE, 2026"), else from the sheet name
        Boolean sales = null;
        YearMonth period = null;
        Matcher m = TITLE.matcher(findTitleText(sheet, headerRow));
        if (m.find()) {
            sales = m.group(1).equalsIgnoreCase("SALES");
            Month month = monthOf(m.group(2));
            if (month != null) period = YearMonth.of(Integer.parseInt(m.group(3)), month);
        }
        if (period == null) {
            Matcher sn = SHEET_NAME.matcher(sheetName);
            if (sn.find()) {
                Month month = monthOf(sn.group(1));
                if (month != null) period = YearMonth.of(Integer.parseInt(sn.group(2)), month);
            }
        }
        if (sales == null) {
            String upper = sheetName.toUpperCase(Locale.ROOT);
            if (upper.contains("SALES")) sales = true;
            else if (upper.contains("PURCHASE")) sales = false;
            else sales = !(has(cols, NAME) || has(cols, TIN));   // supplier columns => purchases
        }

        int cDate = find(cols, DATE);
        int cInvoice = find(cols, INVOICE);
        int cExempt = find(cols, EXEMPT);
        int cZero = find(cols, ZERO);

        if (sales) {
            int cGross = find(cols, SALES_GROSS);
            if (cGross < 0) {
                warnings.add("Sheet \"" + sheetName + "\" skipped: no \"Total Amount\" column.");
                return;
            }
            readSales(sheet, headerRow, period, months, warnings, sheetName,
                    cDate, cInvoice, cExempt, cZero, cGross);
        } else {
            int cGross = find(cols, PURCHASE_GROSS);
            int cVat = find(cols, PURCHASE_VAT);
            int cTin = find(cols, TIN);
            int cName = find(cols, NAME);
            int cAddress = find(cols, ADDRESS);
            if (cGross < 0 && cVat < 0) {
                warnings.add("Sheet \"" + sheetName + "\" skipped: no \"Total Amount of Purchases\" column.");
                return;
            }
            if (cTin < 0 && cName < 0) {
                warnings.add("Sheet \"" + sheetName + "\" skipped: no supplier (Trade Name / TIN) column.");
                return;
            }
            readPurchases(sheet, headerRow, period, months, suppliers, warnings, sheetName,
                    cDate, cInvoice, cTin, cName, cAddress, cExempt, cZero, cGross, cVat);
        }
    }

    private void readSales(Sheet sheet, int headerRow, YearMonth period,
                           Map<YearMonth, ParsedMonth> months, List<String> warnings, String sheetName,
                           int cDate, int cInvoice, int cExempt, int cZero, int cGross) {
        List<ParsedSale> rows = new ArrayList<>();

        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            LocalDate date = date(cell(row, cDate));
            if (date == null) continue;   // rate row, blank rows, Total row, signature block

            BigDecimal gross = amount(cell(row, cGross));
            if (gross == null) {
                warnings.add(sheetName + " row " + (r + 1) + " skipped: no total amount.");
                continue;
            }
            rows.add(new ParsedSale(r + 1, date, text(cell(row, cInvoice)),
                    orZero(amount(cell(row, cExempt))), orZero(amount(cell(row, cZero))), gross));
        }

        YearMonth ym = period != null ? period : firstMonth(rows.isEmpty() ? null : rows.get(0).date());
        if (ym == null) {
            if (!rows.isEmpty()) warnings.add("Sheet \"" + sheetName + "\" skipped: cannot tell which month it is.");
            return;
        }
        for (ParsedSale s : rows) {
            if (!YearMonth.from(s.date()).equals(ym)) {
                warnings.add(sheetName + " row " + s.excelRow() + ": date " + s.date() + " is outside " + ym + ".");
            }
        }
        month(months, ym).sales().addAll(rows);
    }

    private void readPurchases(Sheet sheet, int headerRow, YearMonth period,
                               Map<YearMonth, ParsedMonth> months, Map<String, ParsedSupplier> suppliers,
                               List<String> warnings, String sheetName,
                               int cDate, int cInvoice, int cTin, int cName, int cAddress,
                               int cExempt, int cZero, int cGross, int cVat) {
        List<ParsedPurchase> rows = new ArrayList<>();

        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            LocalDate date = date(cell(row, cDate));
            if (date == null) continue;   // rate row, Sub Total rows, blank rows, Total row, signature block

            String tin = text(cell(row, cTin));
            String name = text(cell(row, cName));
            String address = text(cell(row, cAddress));
            if (tin.isEmpty() && name.isEmpty()) {
                warnings.add(sheetName + " row " + (r + 1) + " skipped: no supplier.");
                continue;
            }

            BigDecimal exempt = orZero(amount(cell(row, cExempt)));
            BigDecimal zero = orZero(amount(cell(row, cZero)));

            BigDecimal gross = amount(cell(row, cGross));
            if (gross == null) {   // fall back to exempt + zero-rated + Total Amount of Vat
                BigDecimal vat = amount(cell(row, cVat));
                if (vat != null) gross = exempt.add(zero).add(vat);
            }
            if (gross == null) {
                warnings.add(sheetName + " row " + (r + 1) + " skipped: no total amount.");
                continue;
            }

            rows.add(new ParsedPurchase(r + 1, date, text(cell(row, cInvoice)),
                    tin, name, address, exempt, zero, gross));

            String key = !tin.isEmpty() ? "tin:" + tin.replaceAll("\\s+", "") : "name:" + name.toLowerCase(Locale.ROOT);
            ParsedSupplier old = suppliers.get(key);
            if (old == null) {
                suppliers.put(key, new ParsedSupplier(tin, name, address));
            } else if (old.tradeName().isEmpty() || old.address().isEmpty()) {   // fill gaps from later rows
                suppliers.put(key, new ParsedSupplier(old.tin(),
                        old.tradeName().isEmpty() ? name : old.tradeName(),
                        old.address().isEmpty() ? address : old.address()));
            }
        }

        YearMonth ym = period != null ? period : firstMonth(rows.isEmpty() ? null : rows.get(0).date());
        if (ym == null) {
            if (!rows.isEmpty()) warnings.add("Sheet \"" + sheetName + "\" skipped: cannot tell which month it is.");
            return;
        }
        for (ParsedPurchase p : rows) {
            if (!YearMonth.from(p.date()).equals(ym)) {
                warnings.add(sheetName + " row " + p.excelRow() + ": date " + p.date() + " is outside " + ym + ".");
            }
        }
        month(months, ym).purchases().addAll(rows);
    }

    // ---------------------------------------------------------
    // SHEET STRUCTURE
    // ---------------------------------------------------------

    private int findHeaderRow(Sheet sheet) {
        int last = Math.min(sheet.getLastRowNum(), HEADER_SEARCH_ROWS);
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell c : row) {
                if (DATE.contains(norm(text(c)))) return r;
            }
        }
        return -1;
    }

    private Map<String, Integer> headerColumns(Row header) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Cell c : header) {
            String key = norm(text(c));
            if (!key.isEmpty()) map.putIfAbsent(key, c.getColumnIndex());
        }
        return map;
    }

    private String findTitleText(Sheet sheet, int headerRow) {
        for (int r = 0; r < headerRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell c : row) {
                String t = text(c);
                if (t.toUpperCase(Locale.ROOT).contains("FOR THE MONTH OF")) return t;
            }
        }
        return "";
    }

    private static int find(Map<String, Integer> cols, Set<String> aliases) {
        for (String a : aliases) {
            Integer i = cols.get(a);
            if (i != null) return i;
        }
        return -1;
    }

    private static boolean has(Map<String, Integer> cols, Set<String> aliases) {
        return find(cols, aliases) >= 0;
    }

    private static ParsedMonth month(Map<YearMonth, ParsedMonth> months, YearMonth ym) {
        return months.computeIfAbsent(ym, k -> new ParsedMonth(k, new ArrayList<>(), new ArrayList<>()));
    }

    private static YearMonth firstMonth(LocalDate d) {
        return d == null ? null : YearMonth.from(d);
    }

    private static Month monthOf(String name) {
        if (name == null || name.length() < 3) return null;
        String three = name.substring(0, 3).toUpperCase(Locale.ROOT);
        for (Month m : Month.values()) {
            if (m.name().startsWith(three)) return m;
        }
        return null;
    }

    // ---------------------------------------------------------
    // CELL READING
    // ---------------------------------------------------------

    private static Cell cell(Row row, int col) {
        return col < 0 ? null : row.getCell(col);
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String text(Cell cell) {
        if (cell == null) return "";
        try {
            return formatter.formatCellValue(cell, evaluator).replaceAll("\\s+", " ").trim();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private LocalDate date(Cell cell) {
        if (cell == null) return null;
        try {
            CellType type = cell.getCellType() == CellType.FORMULA
                    ? cell.getCachedFormulaResultType() : cell.getCellType();

            if (type == CellType.NUMERIC) {
                // a plain number (e.g. the 1.12 rate cell) is not a date; only date-formatted cells count
                return DateUtil.isCellDateFormatted(cell)
                        ? cell.getLocalDateTimeCellValue().toLocalDate() : null;
            }
            if (type == CellType.STRING) {
                String s = cell.getStringCellValue().trim();
                for (DateTimeFormatter f : DATE_FORMATS) {
                    try {
                        return LocalDate.parse(s, f);
                    } catch (DateTimeParseException ignored) {
                        // try the next format
                    }
                }
            }
        } catch (RuntimeException ex) {
            // unreadable cell: treat as "no date"
        }
        return null;
    }

    /** Numbers, formulas, or text such as "1,234.50", "₱ 1,234.50", "(500.00)", "-". Null if not a number. */
    private BigDecimal amount(Cell cell) {
        if (cell == null) return null;
        try {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) {
                CellValue v = evaluator.evaluate(cell);
                if (v == null) return null;
                if (v.getCellType() == CellType.NUMERIC) return money(v.getNumberValue());
                if (v.getCellType() == CellType.STRING) return parseMoney(v.getStringValue());
                return null;
            }
            if (type == CellType.NUMERIC) return money(cell.getNumericCellValue());
            if (type == CellType.STRING) return parseMoney(cell.getStringCellValue());
        } catch (RuntimeException ex) {
            // formula could not be evaluated: use the value Excel last saved
            try {
                if (cell.getCellType() == CellType.FORMULA
                        && cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                    return money(cell.getNumericCellValue());
                }
            } catch (RuntimeException ignored) {
                // give up on this cell
            }
        }
        return null;
    }

    private static BigDecimal money(double d) {
        return BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal parseMoney(String raw) {
        if (raw == null) return null;
        String s = raw.replace("\u20B1", "").replace("PHP", "").replace(",", "").replaceAll("\\s+", "");
        if (s.isEmpty() || s.equals("-")) return BigDecimal.ZERO;
        boolean negative = s.startsWith("(") && s.endsWith(")");
        if (negative) s = s.substring(1, s.length() - 1);
        try {
            BigDecimal v = new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
            return negative ? v.negate() : v;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}