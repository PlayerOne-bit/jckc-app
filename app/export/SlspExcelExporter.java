package app.export;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import app.models.Name;
import app.models.Purchase;
import app.models.SLSP;
import app.models.Sale;
import app.models.Supplier;
import app.models.Taxpayer;

/**
 * Writes SLSP data to an Excel workbook laid out like the accountant's monthly workbook:
 * one sheet per month (named like "JUN 2026"), a business / owner / address / TIN banner,
 * "VAT PURCHASES FOR THE MONTH OF ..." title, a two-row header, entries on every other row,
 * a Total row with SUM formulas, and a "Certified True and Correct" signature block.
 *
 * The Purchases layout copies the supplied workbook (VAT Purchases = Total Amount of Vat / 1.12,
 * Input Tax = VAT Purchases x 12%, Total Amount of Purchases = VAT Exempt + Total Amount of Vat).
 * The Sales layout follows the same style; the supplied file had no Sales sheet to copy.
 */
public final class SlspExcelExporter {

    public enum Kind { SALES, PURCHASES }
    private record Layout(String title, String[] headers, Double[] rates, double[] widths) {}

    // Column order and widths copied from the "JUN 2026" sheet
    private static final Layout PURCHASES = new Layout(
            "VAT PURCHASES",
            new String[] { "Date", "Trade Name", "Address", "SALES INVOICE NO.", "TIN", "VAT Exempt",
                    "Zero-rated", "VAT Purchases", "Input Tax", "Total Amount of Vat",
                    "Total Amount of Purchases" },
            new Double[] { null, null, null, null, null, null, null, 1.12, 0.12, null, null },
            new double[] { 10.7, 26.3, 37.3, 10.7, 14.6, 12.4, 12.4, 14.6, 12.3, 14.0, 16.0 });

    private static final Layout SALES = new Layout(
            "VAT SALES",
            new String[] { "Date", "Sales Invoice No.", "VAT Exempt", "Zero-rated", "VAT Receipts",
                    "Output Tax", "Total Amount" },
            new Double[] { null, null, null, null, 1.12, 0.12, null },
            new double[] { 10.7, 18.0, 14.0, 14.0, 14.6, 14.0, 16.0 });

    private static final int FIRST_DATA_ROW = 9;   // zero-based, i.e. Excel row 10
    private static final String TOTAL_LABEL = "Total   " + "- ".repeat(37).strip();

    private static final String ACCOUNTING = "_(* #,##0.00_);_(* \\(#,##0.00\\);_(* \"-\"??_);_(@_)";
    private static final String PESO =
            "_(\"\u20B1\"* #,##0.00_);_(\"\u20B1\"* \\(#,##0.00\\);_(\"\u20B1\"* \"-\"??_);_(@_)";

    private final Taxpayer taxpayer;
    private final Map<Integer, Supplier> suppliers;

    public SlspExcelExporter(
            Taxpayer taxpayer,
            Map<Integer, Supplier> suppliersById) {

        if (taxpayer == null) {
            throw new IllegalArgumentException(
                    "A taxpayer must be selected before exporting."
            );
        }

        this.taxpayer = taxpayer;
        this.suppliers = suppliersById == null
                ? Map.of()
                : Map.copyOf(suppliersById);
    }

    /** The Purchases layout has no Zero-rated column, so callers may want to warn about these. */
    public static boolean hasZeroRatedPurchases(List<SLSP> slsps) {
        for (SLSP slsp : slsps) {
            if (slsp.getPurchases() == null) continue;
            for (Purchase p : slsp.getPurchases()) {
                if (p.getZeroRatedAmount() != null && p.getZeroRatedAmount().signum() != 0) return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------
    // PUBLIC ENTRY POINT
    // ---------------------------------------------------------

    /** Writes one workbook with one sheet per SLSP (month), in the order given. */
    /**
     * Exports either Sales or Purchases using the accountant-style layout.
     */
    public void write(
            Kind kind,
            List<SLSP> slsps,
            OutputStream out) throws IOException {

        if (kind == null) {
            throw new IllegalArgumentException("Select an export type.");
        }

        write(
                kind == Kind.SALES,
                kind == Kind.PURCHASES,
                slsps,
                out
        );
    }

    /**
     * Exports Sales, Purchases, or both using the accountant-style layout.
     * Creates a separate worksheet for each month and report type.
     */
    public void write(
            boolean includeSales,
            boolean includePurchases,
            List<SLSP> slsps,
            OutputStream out) throws IOException {

        if (slsps == null || slsps.isEmpty()) {
            throw new IllegalArgumentException("Nothing to export.");
        }

        if (!includeSales && !includePurchases) {
            throw new IllegalArgumentException(
                    "Select Sales and/or Purchases."
            );
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            Styles styles = new Styles(workbook);

            // Sort records chronologically before creating worksheets.
            List<SLSP> sorted = new ArrayList<>(slsps);

            sorted.sort(
                    Comparator.comparing(SLSP::getYearMonth)
            );

            if (includeSales) {
                for (SLSP slsp : sorted) {
                    if (slsp != null) writeSales(workbook, styles, slsp);
                }
            }

            if (includePurchases) {
                for (SLSP slsp : sorted) {
                    if (slsp != null) writePurchases(workbook, styles, slsp);
                }
            }

            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException(
                        "No worksheets could be created."
                );
            }

            workbook.setForceFormulaRecalculation(true);

            workbook.getCreationHelper()
                    .createFormulaEvaluator()
                    .evaluateAll();

            workbook.write(out);
        }
    }

    // ---------------------------------------------------------
    // PURCHASES SHEET
    // ---------------------------------------------------------

    private void writePurchases(Workbook workbook, Styles st, SLSP slsp) {
    	Sheet sheet = newSheet(
    	        workbook,
    	        st,
    	        slsp,
    	        PURCHASES,
    	        "PURCHASES"
    	);

        List<Purchase> all = new ArrayList<>(slsp.getPurchases() == null ? List.of() : slsp.getPurchases());
        // Group by supplier (A-Z), then date, then invoice, like the accountant's sheet
        all.sort(Comparator
                .comparing((Purchase p) -> supplierName(p).toLowerCase(Locale.ROOT))
                .thenComparingInt(Purchase::getSupplierId)
                .thenComparing(p -> LocalDate.parse(p.getPurchaseDate()))
                .thenComparing(p -> String.valueOf(p.getInvoiceNum())));

        Map<Integer, List<Purchase>> groups = new java.util.LinkedHashMap<>();
        for (Purchase p : all) groups.computeIfAbsent(p.getSupplierId(), _ -> new ArrayList<>()).add(p);

        List<Integer> totalParts = new ArrayList<>();   // Excel rows feeding the grand total
        int r = FIRST_DATA_ROW;

        for (List<Purchase> group : groups.values()) {
            int firstExcel = r + 1;
            for (Purchase p : group) {
                Supplier s = suppliers == null ? null : suppliers.get(p.getSupplierId());
                String name = s != null ? s.getTradeName() : "Unknown Supplier";
                String address = s != null ? s.getBussAddress() : "";
                String tin = s != null ? s.getTinNum() : "";

                Row row = sheet.createRow(r);
                int x = r + 1;
                date(row, 0, p.getPurchaseDate(), st.date);
                text(row, 1, name, st.textLeft);
                text(row, 2, address, st.textLeft);
                text(row, 3, p.getInvoiceNum(), st.textCenter);   // D  SALES INVOICE NO.
                text(row, 4, tin, st.textCenter);                 // E  TIN
                number(row, 5, p.getExemptAmount(), st.amount);      // F  VAT Exempt (typed)
                number(row, 6, p.getZeroRatedAmount(), st.amount);   // G  Zero-rated (typed)
                formula(row, 9, nz(p.getGrossAmount()).toPlainString() + "-F" + x + "-G" + x, st.amount);   // J  Total Amount of Vat
                formula(row, 7, "J" + x + "/1.12", st.amount);                // H  VAT Purchases
                formula(row, 8, "H" + x + "*0.12", st.amount);                // I  Input Tax
                formula(row, 10, "F" + x + "+G" + x + "+J" + x, st.amount);   // K  Total Amount of Purchases
                row.setHeightInPoints(heightFor(name, address));
                r++;
            }
            int lastExcel = r;

            if (group.size() > 1) {
                Row sub = sheet.createRow(r);
                int x = r + 1;
                text(sub, 4, "Sub Total - - -", st.subLabel);
                formula(sub, 5, "SUM(F" + firstExcel + ":F" + lastExcel + ")", st.subAmount);
                formula(sub, 6, "SUM(G" + firstExcel + ":G" + lastExcel + ")", st.subAmount);
                formula(sub, 7, "J" + x + "/1.12", st.subAmount);
                formula(sub, 8, "H" + x + "*0.12", st.subAmount);
                formula(sub, 9, "SUM(J" + firstExcel + ":J" + lastExcel + ")", st.subAmount);
                formula(sub, 10, "F" + x + "+G" + x + "+J" + x, st.subAmount);
                totalParts.add(x);
                r++;
            } else {
                totalParts.add(lastExcel);
            }
            r++;   // blank row after every group
        }

        purchasesTotal(sheet, st, r + 1, totalParts);
    }

    private String supplierName(Purchase p) {
        Supplier s = suppliers == null ? null : suppliers.get(p.getSupplierId());
        return s != null ? nzs(s.getTradeName()) : "Unknown Supplier";
    }

    /** Total adds only the subtotal rows and single-row groups, so nothing is counted twice. */
    private void purchasesTotal(Sheet sheet, Styles st, int r, List<Integer> parts) {
        Row row = sheet.createRow(r);
        int x = r + 1;

        Cell label = row.createCell(2);
        label.setCellValue(TOTAL_LABEL);
        label.setCellStyle(st.totalLabel);

        for (int c = 5; c <= 10; c++) {
            Cell cell = row.createCell(c);
            cell.setCellStyle(st.totalAmount);
            String col = CellReference.convertNumToColString(c);
            switch (c) {
                case 7 -> cell.setCellFormula("J" + x + "/1.12");
                case 8 -> cell.setCellFormula("H" + x + "*0.12");
                case 10 -> cell.setCellFormula("F" + x + "+G" + x + "+J" + x);
                default -> {   // F, G and J
                    if (parts.isEmpty()) cell.setCellValue(0);
                    else cell.setCellFormula("SUM(" + String.join(",",
                            parts.stream().map(n -> col + n).toList()) + ")");
                }
            }
        }

        text(sheet.createRow(r + 4), 7, "Certified True & Correct:", st.plain);
        text(sheet.createRow(r + 8), 7, ownerName(), st.plain);
        text(sheet.createRow(r + 9), 7, "            Owner/Taxpayer", st.plain);
    }

    // ---------------------------------------------------------
    // SALES SHEET
    // ---------------------------------------------------------

    private void writeSales(Workbook workbook, Styles st, SLSP slsp) {
    	Sheet sheet = newSheet(
    	        workbook,
    	        st,
    	        slsp,
    	        SALES,
    	        "SALES"
    	);

        List<Sale> rows = sortedByDate(slsp.getSales(), Sale::getSaleDate, Sale::getSiNum);

        int r = FIRST_DATA_ROW;
        for (Sale s : rows) {
            Row row = sheet.createRow(r);
            int x = r + 1;

            date(row, 0, s.getSaleDate(), st.date);
            text(row, 1, s.getSiNum(), st.textCenter);
            number(row, 2, s.getExemptAmount(), st.amount);      // C  VAT Exempt (typed)
            number(row, 3, s.getZeroRatedAmount(), st.amount);   // D  Zero-rated (typed)
            formula(row, 4, "(G" + x + "-C" + x + "-D" + x + ")/1.12", st.amount);    // E  VAT Receipts = (Total Amount - VAT Exempt - Zero-rated) / 1.12
            formula(row, 5, "E" + x + "*0.12", st.amount);       // F  Output Tax = VAT Receipts x 12%
            number(row, 6, s.getGrossAmount(), st.amount);    // G  Total Amount

            row.setHeightInPoints(15f);   // was 25.5; rows sit back to back now
            r++;  
        }

        totals(sheet, st, r, 1, 2, 6, 3, !rows.isEmpty());
    }

    // ---------------------------------------------------------
    // SHEET SCAFFOLD: banner, title, two-row header, page setup
    // ---------------------------------------------------------

    @SuppressWarnings("deprecation")
    private Sheet newSheet(
            Workbook workbook,
            Styles st,
            SLSP slsp,
            Layout layout,
            String sheetSuffix) {

        YearMonth ym = slsp.getYearMonth();

        String abbreviation = ym.getMonth()
                .getDisplayName(
                        TextStyle.SHORT,
                        Locale.ENGLISH
                )
                .toUpperCase(Locale.ROOT);

        String fullMonth = ym.getMonth()
                .getDisplayName(
                        TextStyle.FULL,
                        Locale.ENGLISH
                )
                .toUpperCase(Locale.ROOT);

        // Distinguish Sales and Purchases worksheets for the same month.
        String sheetName = abbreviation + " " + ym.getYear()
                + " " + sheetSuffix;

        Sheet sheet = workbook.createSheet(sheetName);

        int last = layout.headers().length - 1;

        for (int c = 0; c <= last; c++) {
            sheet.setColumnWidth(
                    c,
                    (int) (layout.widths()[c] * 256)
            );
        }

        // Rows 1-4: business, owner, address, TIN.
        banner(
                sheet, 0,
                taxpayer.getTradeName(),
                st.businessLine, last
        );

        banner(
                sheet, 1,
                ownerName(),
                st.businessLine, last
        );

        banner(
                sheet, 2,
                taxpayer.getBussAddress(),
                st.businessLine, last
        );

        banner(
                sheet, 3,
                "TIN   " + nzs(taxpayer.getTinNum()),
                st.boldLine, last
        );

        // Row 6: report title.
        banner(
                sheet, 5,
                layout.title()
                        + " FOR THE MONTH OF "
                        + fullMonth
                        + ", "
                        + ym.getYear(),
                st.boldLine, last
        );

        // Rows 8-9: two-row header.
        Row top = sheet.createRow(7);
        Row bottom = sheet.createRow(8);

        top.setHeightInPoints(28f);
        bottom.setHeightInPoints(14f);

        for (int c = 0; c <= last; c++) {

            Cell upper = top.createCell(c);
            Cell lower = bottom.createCell(c);

            upper.setCellStyle(st.header);
            lower.setCellStyle(st.header);

            upper.setCellValue(layout.headers()[c]);

            Double rate = layout.rates()[c];

            if (rate == null) {

                sheet.addMergedRegion(
                        new CellRangeAddress(7, 8, c, c)
                );

            } else {

                lower.setCellValue(rate);

                lower.setCellStyle(
                        rate >= 1
                                ? st.headerNumber
                                : st.headerPercent
                );
            }
        }

        // Landscape A4, one page wide.
        sheet.setFitToPage(true);

        PrintSetup setup = sheet.getPrintSetup();

        setup.setLandscape(true);
        setup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        setup.setFitWidth((short) 1);
        setup.setFitHeight((short) 0);

        sheet.setHorizontallyCenter(true);

        sheet.setMargin(Sheet.LeftMargin, 0.4);
        sheet.setMargin(Sheet.RightMargin, 0.4);
        sheet.setMargin(Sheet.TopMargin, 0.75);
        sheet.setMargin(Sheet.BottomMargin, 0.75);

        return sheet;
    }

    private void banner(Sheet sheet, int rowIndex, String text, CellStyle style, int lastColumn) {
        Row row = sheet.createRow(rowIndex);
        for (int c = 0; c <= lastColumn; c++) row.createCell(c).setCellStyle(style);
        row.getCell(0).setCellValue(nzs(text));
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, lastColumn));
    }

    /** Total row with SUM formulas, then the "Certified True & Correct" block below it. */
    private void totals(Sheet sheet, Styles st, int r, int labelColumn, int firstAmountColumn,
                        int lastColumn, int signatureColumn, boolean hasRows) {
        Row row = sheet.createRow(r);

        Cell label = row.createCell(labelColumn);
        label.setCellValue(TOTAL_LABEL);
        label.setCellStyle(st.totalLabel);

        for (int c = firstAmountColumn; c <= lastColumn; c++) {
            Cell cell = row.createCell(c);
            cell.setCellStyle(st.totalAmount);
            if (hasRows) {
                String column = CellReference.convertNumToColString(c);
                // r is the zero-based total row, so it is also the Excel number of the row just above it
                cell.setCellFormula("SUM(" + column + (FIRST_DATA_ROW + 1) + ":" + column + r + ")");
            } else {
                cell.setCellValue(0);
            }
        }

        text(sheet.createRow(r + 4), signatureColumn, "Certified True & Correct:", st.plain);
        text(sheet.createRow(r + 8), signatureColumn, ownerName(), st.plain);
        text(sheet.createRow(r + 9), signatureColumn, "            Owner/Taxpayer", st.plain);
    }

    // ---------------------------------------------------------
    // SMALL HELPERS
    // ---------------------------------------------------------

    private String ownerName() {
        Name n = taxpayer.getName();
        if (n == null) return "";
        String full = String.join(" ",
                nzs(n.getFirstName()), nzs(n.getMiddleName()), nzs(n.getLastName()), nzs(n.getSuffix()));
        return full.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private static <T> List<T> sortedByDate(List<T> rows, Function<T, String> date, Function<T, String> reference) {
        List<T> copy = new ArrayList<>(rows == null ? List.of() : rows);
        copy.sort(Comparator
                .comparing((T t) -> LocalDate.parse(date.apply(t)))
                .thenComparing(t -> String.valueOf(reference.apply(t))));
        return copy;
    }

    /** Rows are at least 25.5 pt (like the original) and grow for long names / addresses. */
    private static float heightFor(String name, String address) {
        int nameLines = (int) Math.ceil(nzs(name).length() / 28.0);
        int addressLines = (int) Math.ceil(nzs(address).length() / 40.0);
        return Math.max(25.5f, Math.max(1, Math.max(nameLines, addressLines)) * 13.5f);
    }

    private static String nzs(String s) {
        return s == null ? "" : s.trim();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void number(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(nz(value).doubleValue());
        cell.setCellStyle(style);
    }

    private static void formula(Row row, int column, String formula, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellFormula(formula);
        cell.setCellStyle(style);
    }

    private static void date(Row row, int column, String iso, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(LocalDate.parse(iso));
        cell.setCellStyle(style);
    }

    // ---------------------------------------------------------
    // STYLES (fonts and formats copied from the original sheet)
    // ---------------------------------------------------------

    private static final class Styles {
        final CellStyle businessLine, boldLine, header, headerNumber, headerPercent;
        final CellStyle date, textLeft, textCenter, amount, totalLabel, totalAmount, plain;
        final CellStyle subLabel;
        final CellStyle subAmount;
        Styles(Workbook wb) {
            businessLine = make(wb, "Times New Roman", 11, false, false,
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER, false, false, null);
            boldLine = make(wb, "Calibri", 11, true, false,
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER, false, false, null);

            header = make(wb, "Calibri", 10, false, false,
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER, true, true, null);
            headerNumber = make(wb, "Calibri", 10, false, false,
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER, false, true, "0.00");
            headerPercent = make(wb, "Calibri", 10, false, false,
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER, false, true, "0%");

            date = make(wb, "Calibri", 10, false, false,
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER, false, false, null);
            date.setDataFormat((short) 14);   // the built-in short date, same as the original

            textLeft = make(wb, "Calibri", 10, false, false,
                    HorizontalAlignment.LEFT, VerticalAlignment.CENTER, true, false, null);
            textCenter = make(wb, "Calibri", 10, false, false,
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER, true, false, null);
            amount = make(wb, "Calibri", 10, true, false,
                    HorizontalAlignment.RIGHT, VerticalAlignment.CENTER, false, false, ACCOUNTING);

            totalLabel = make(wb, "Calibri", 11, true, false,
                    HorizontalAlignment.LEFT, VerticalAlignment.CENTER, false, false, null);
            totalAmount = make(wb, "Calibri", 10, true, true,
                    HorizontalAlignment.RIGHT, VerticalAlignment.CENTER, false, false, PESO);
            plain = make(wb, "Calibri", 11, false, false,
                    HorizontalAlignment.LEFT, VerticalAlignment.CENTER, false, false, null);
            subLabel = make(wb, "Calibri", 10, true, false,
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER,
                    false, false, null);

            subAmount = make(wb, "Calibri", 10, true, false,
                    HorizontalAlignment.RIGHT, VerticalAlignment.CENTER,
                    false, false, ACCOUNTING);
        }

        private static CellStyle make(Workbook wb, String fontName, int size, boolean bold, boolean underline,
                                      HorizontalAlignment h, VerticalAlignment v, boolean wrap,
                                      boolean border, String format) {
            Font font = wb.createFont();
            font.setFontName(fontName);
            font.setFontHeightInPoints((short) size);
            font.setBold(bold);
            if (underline) font.setUnderline(Font.U_SINGLE);

            CellStyle style = wb.createCellStyle();
            style.setFont(font);
            style.setAlignment(h);
            style.setVerticalAlignment(v);
            style.setWrapText(wrap);
            if (border) {
                style.setBorderTop(BorderStyle.THIN);
                style.setBorderBottom(BorderStyle.THIN);
                style.setBorderLeft(BorderStyle.THIN);
                style.setBorderRight(BorderStyle.THIN);
            }
            if (format != null) style.setDataFormat(wb.createDataFormat().getFormat(format));
            return style;
        }
    }
}