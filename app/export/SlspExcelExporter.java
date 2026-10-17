package app.export;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
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
 * Writes SLSP data to an Excel workbook laid out like the accountant's workbooks:
 *
 *  - one Sales sheet and one Purchases sheet per month (e.g. "JUN 2026 SALES"), with a
 *    business / owner / address / TIN banner, a title, a two-row header, a Total row with
 *    formulas and a "Certified True & Correct" block;
 *  - Purchases rows are grouped by supplier (A-Z) with "Sub Total" rows;
 *  - a last sheet "SLSP YYYY" with the quarterly income / purchases / tax due summary.
 *
 * Editable (typed) columns: VAT Exempt, Zero-rated, and the Total Amount
 * (Sales: Total Amount, Purchases: Total Amount of Vat). Everything else is a formula.
 */
public final class SlspExcelExporter {

    public enum Kind { SALES, PURCHASES }

    private record Layout(String title, String[] headers, Double[] rates, double[] widths) {}

    /** Where a month's Total row lives, so the yearly summary can link to it. */
    private record Ref(String sheet, int row) {
        String cell(String column) {
            return "'" + sheet.replace("'", "''") + "'!" + column + row;
        }
    }

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

    private final Map<Kind, boolean[]> visibleColumns = new EnumMap<>(Kind.class);
    private boolean includeSummary = true;
    private final Map<YearMonth, Ref> salesRefs = new HashMap<>();
    private final Map<YearMonth, Ref> purchaseRefs = new HashMap<>();

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
    public SlspExcelExporter setIncludeSummary(boolean include) {
        this.includeSummary = include;
        return this;
    }

    /**
     * Kept for compatibility. The Purchases sheet now has a Zero-rated column,
     * so callers no longer need to warn about zero-rated purchases.
     */
    public static boolean hasZeroRatedPurchases(List<SLSP> slsps) {
        for (SLSP slsp : slsps) {
            if (slsp.getPurchases() == null) continue;
            for (Purchase p : slsp.getPurchases()) {
                if (p.getZeroRatedAmount() != null && p.getZeroRatedAmount().signum() != 0) return true;
            }
        }
        return false;
    }

    /** Columns set to false are hidden (not removed), so the formulas keep working. */
    public SlspExcelExporter setVisibleColumns(Kind kind, boolean[] visible) {
        if (visible == null) visibleColumns.remove(kind);
        else visibleColumns.put(kind, visible.clone());
        return this;
    }

    // ---------------------------------------------------------
    // PUBLIC ENTRY POINTS
    // ---------------------------------------------------------

    /** Exports either Sales or Purchases using the accountant-style layout. */
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
     * Exports Sales, Purchases, or both. Sheet order: all Sales months (Jan to Dec),
     * then all Purchases months (Jan to Dec), then the "SLSP YYYY" summary sheet(s).
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

            salesRefs.clear();
            purchaseRefs.clear();

            // Sort records chronologically before creating worksheets.
            List<SLSP> sorted = new ArrayList<>(slsps);
            sorted.sort(Comparator.comparing(SLSP::getYearMonth));

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
                throw new IllegalArgumentException("No worksheets could be created.");
            }
            if (includeSummary) {
                // Written last because it links to the month sheets' Total rows, then moved to the front
                List<String> names = writeSummarySheets(workbook, styles, sorted, includeSales, includePurchases);
                for (int i = 0; i < names.size(); i++) {
                    workbook.setSheetOrder(names.get(i), i);
                }
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    workbook.getSheetAt(i).setSelected(i == 0);   // only the first tab selected, so Excel doesn't group sheets
                }
                workbook.setActiveSheet(0);
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
        Sheet sheet = newSheet(workbook, st, slsp, PURCHASES, "PURCHASES");

        List<Purchase> all = new ArrayList<>(slsp.getPurchases() == null ? List.of() : slsp.getPurchases());
        // Group by supplier (A-Z), then date, then invoice, like the accountant's sheet
        all.sort(Comparator
                .comparing((Purchase p) -> supplierName(p).toLowerCase(Locale.ROOT))
                .thenComparingInt(Purchase::getSupplierId)
                .thenComparing(p -> LocalDate.parse(p.getPurchaseDate()))
                .thenComparing(p -> String.valueOf(p.getInvoiceNum())));

        Map<Integer, List<Purchase>> groups = new LinkedHashMap<>();
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
                text(row, 3, p.getInvoiceNum(), st.textCenter);      // D  SALES INVOICE NO.
                text(row, 4, tin, st.textCenter);                    // E  TIN
                number(row, 5, p.getExemptAmount(), st.amount);      // F  VAT Exempt (typed)
                number(row, 6, p.getZeroRatedAmount(), st.amount);   // G  Zero-rated (typed)
                // J  Total Amount of Vat = invoice total - VAT Exempt - Zero-rated (like =4439.6-F10)
                formula(row, 9, nz(p.getGrossAmount()).toPlainString() + "-F" + x + "-G" + x, st.amount);
                formula(row, 7, "J" + x + "/1.12", st.amount);               // H  VAT Purchases
                formula(row, 8, "H" + x + "*0.12", st.amount);               // I  Input Tax
                formula(row, 10, "F" + x + "+G" + x + "+J" + x, st.amount);  // K  Total Amount of Purchases
                row.setHeightInPoints(heightFor(name, address));
                r++;
            }
            int lastExcel = r;

            if (group.size() > 1) {
                Row sub = sheet.createRow(r);
                int x = r + 1;
                text(sub, anchor(Kind.PURCHASES, 4), "Sub Total - - -", st.subLabel);
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

        // The Total row is written at index r + 1, i.e. Excel row r + 2
        purchaseRefs.put(slsp.getYearMonth(), new Ref(sheet.getSheetName(), r + 2));
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

        Cell label = row.createCell(anchor(Kind.PURCHASES, 2));
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

        int sig = anchor(Kind.PURCHASES, 7);
        text(sheet.createRow(r + 4), sig, "Certified True & Correct:", st.plain);
        text(sheet.createRow(r + 8), sig, ownerName(), st.plain);
        text(sheet.createRow(r + 9), sig, "            Owner/Taxpayer", st.plain);
    }

    // ---------------------------------------------------------
    // SALES SHEET
    // ---------------------------------------------------------

    private void writeSales(Workbook workbook, Styles st, SLSP slsp) {
        Sheet sheet = newSheet(workbook, st, slsp, SALES, "SALES");

        List<Sale> rows = sortedByDate(slsp.getSales(), Sale::getSaleDate, Sale::getSiNum);

        int r = FIRST_DATA_ROW;
        for (Sale s : rows) {
            Row row = sheet.createRow(r);
            int x = r + 1;

            date(row, 0, s.getSaleDate(), st.date);
            text(row, 1, s.getSiNum(), st.textCenter);
            number(row, 2, s.getExemptAmount(), st.amount);      // C  VAT Exempt (typed)
            number(row, 3, s.getZeroRatedAmount(), st.amount);   // D  Zero-rated (typed)
            // E  VAT Receipts = (Total Amount - VAT Exempt - Zero-rated) / 1.12
            formula(row, 4, "(G" + x + "-C" + x + "-D" + x + ")/1.12", st.amount);
            formula(row, 5, "E" + x + "*0.12", st.amount);       // F  Output Tax = VAT Receipts x 12%
            number(row, 6, s.getGrossAmount(), st.amount);       // G  Total Amount (typed)

            row.setHeightInPoints(15f);
            r++;
        }

        // The Total row is written at index r, i.e. Excel row r + 1
        salesRefs.put(slsp.getYearMonth(), new Ref(sheet.getSheetName(), r + 1));
        totals(sheet, st, r, anchor(Kind.SALES, 1), 2, 6, anchor(Kind.SALES, 3), !rows.isEmpty());
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
                .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                .toUpperCase(Locale.ROOT);

        String fullMonth = ym.getMonth()
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                .toUpperCase(Locale.ROOT);

        // Distinguish Sales and Purchases worksheets for the same month.
        String sheetName = abbreviation + " " + ym.getYear() + " " + sheetSuffix;

        Sheet sheet = workbook.createSheet(sheetName);

        int last = layout.headers().length - 1;

        Kind kind = layout == PURCHASES ? Kind.PURCHASES : Kind.SALES;

        for (int c = 0; c <= last; c++) {
            sheet.setColumnWidth(c, (int) (layout.widths()[c] * 256));
            sheet.setColumnHidden(c, !isVisible(kind, c));
        }

        // Rows 1-4: business, owner, address, TIN.
        banner(sheet, 0, taxpayer.getTradeName(), st.businessLine, last);
        banner(sheet, 1, ownerName(), st.businessLine, last);
        banner(sheet, 2, taxpayer.getBussAddress(), st.businessLine, last);
        banner(sheet, 3, "TIN   " + nzs(taxpayer.getTinNum()), st.boldLine, last);

        // Row 6: report title.
        banner(sheet, 5,
                layout.title() + " FOR THE MONTH OF " + fullMonth + ", " + ym.getYear(),
                st.boldLine, last);

        // Rows 8-9: two-row header. Columns without a rate (1.12 / 12%) span both rows.
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
                sheet.addMergedRegion(new CellRangeAddress(7, 8, c, c));
            } else {
                lower.setCellValue(rate);
                lower.setCellStyle(rate >= 1 ? st.headerNumber : st.headerPercent);
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
    // YEARLY SUMMARY SHEET: "SUMMARY YYYY" (quarterly income / purchases / tax due)
    // ---------------------------------------------------------

    private List<String> writeSummarySheets(Workbook workbook, Styles st, List<SLSP> slsps,
                                            boolean includeSales, boolean includePurchases) {
        List<String> names = new ArrayList<>();

        Map<Integer, TreeSet<Integer>> quartersByYear = new TreeMap<>();
        for (SLSP s : slsps) {
            if (s == null) continue;
            YearMonth ym = s.getYearMonth();
            quartersByYear.computeIfAbsent(ym.getYear(), _ -> new TreeSet<>())
                    .add((ym.getMonthValue() - 1) / 3 + 1);
        }

        for (Map.Entry<Integer, TreeSet<Integer>> entry : quartersByYear.entrySet()) {
            Sheet sheet = workbook.createSheet("Summary " + entry.getKey());
            names.add(sheet.getSheetName());

            double[] widths = { 12.9, 14.7, 14.7, 14.7, 14.7, 15.7 };
            for (int c = 0; c < widths.length; c++) {
                sheet.setColumnWidth(c, (int) (widths[c] * 256));
            }

            // Header: once, at the top
            banner(sheet, 0, taxpayer.getTradeName(), st.sumBanner, 5);
            banner(sheet, 1, taxpayer.getBussAddress(), st.sumBanner, 5);
            banner(sheet, 2, ownerLegalName(), st.sumBanner, 5);
            banner(sheet, 3, "TIN " + nzs(taxpayer.getTinNum()), st.sumBanner, 5);

            // Quarters in order: Q1 (income, purchases, tax due), Q2, ...
            int next = 7;   // first title row, like row 8 in the accountant's file
            int last = next;
            for (int quarter : entry.getValue()) {
                last = writeQuarter(sheet, st, next, entry.getKey(), quarter,
                        includeSales, includePurchases);
                next = last + 3;
            }

            // Signature: once, at the very bottom
            int cert = last + 2;
            text(sheet.createRow(cert), 2, "Certified True & Correct:", st.sumSign);
            text(sheet.createRow(cert + 4), 2, ownerName(), st.sumSign);
            text(sheet.createRow(cert + 5), 2, "             Owner/Taxpayer", st.sumSign);

            sheet.setFitToPage(true);
            PrintSetup setup = sheet.getPrintSetup();
            setup.setPaperSize(PrintSetup.A4_PAPERSIZE);   // portrait
            setup.setFitWidth((short) 1);
            setup.setFitHeight((short) 0);
            sheet.setHorizontallyCenter(true);
        }

        return names;
    }

    /** Writes one quarter (banner, income, purchases, tax due, signature). Returns its last row index. */
    private int writeQuarter(Sheet sheet, Styles st, int start, int year, int quarter,
            boolean includeSales, boolean includePurchases) {
		String ordinal = switch (quarter) {
			case 1 -> "1ST";
			case 2 -> "2ND";
			case 3 -> "3RD";
			default -> "4TH";
		};
		
		List<YearMonth> months = new ArrayList<>();
		for (int m = 0; m < 3; m++) months.add(YearMonth.of(year, (quarter - 1) * 3 + 1 + m));
		
		int r = start;
		int salesTotal = -1, purchaseTotal = -1, last = r;
		
		if (includeSales) {
			banner(sheet, r, "INCOME FOR THE " + ordinal + " QTR " + year, st.sumTitle, 5);
			salesTotal = summaryTable(sheet, st, r + 2, new String[] {
			   "MONTHS", "Exempt Sales / Receipts", "Sales to Government",
			   "Vatable Sales / Receipts - Private", "Output Tax           12%",
			   "Total Sales/Receipts and Output Tax" },
			   months, salesRefs, "C", "E");
			last = salesTotal;
			r = salesTotal + 3;
		}
		
		if (includePurchases) {
			banner(sheet, r, "PURCHASES FOR THE " + ordinal + " QTR " + year, st.sumTitle, 5);
			purchaseTotal = summaryTable(sheet, st, r + 2, new String[] {
			   "MONTHS", "Purchases Not Qualified for Input Tax", "Domestic Purchase of Services",
			   "Domestic Purchase of Goods Other than Capital Goods", "Input Tax           12%",
			   "Total Purchases and Input Tax" },
			   months, purchaseRefs, "F", "H");
			last = purchaseTotal;
		}
		
		if (includeSales && includePurchases) {
			last = purchaseTotal + 2;
			Row row = sheet.createRow(last);
			text(row, 1, "Tax Due   " + "- ".repeat(23).strip(), st.totalLabel);
			formula(row, 4, "E" + (salesTotal + 1) + "-E" + (purchaseTotal + 1), st.sumTotal);
		}
		
		return last;
	}

    /** Header + three month rows + Total row. Returns the zero-based index of the Total row. */
    private int summaryTable(Sheet sheet, Styles st, int headerTop, String[] headers,
                             List<YearMonth> months, Map<YearMonth, Ref> refs,
                             String exemptColumn, String vatableColumn) {
        Row[] header = { sheet.createRow(headerTop), sheet.createRow(headerTop + 1),
                sheet.createRow(headerTop + 2) };
        header[0].setHeightInPoints(15f);
        header[1].setHeightInPoints(15f);
        header[2].setHeightInPoints(26.25f);

        for (int c = 0; c < headers.length; c++) {
            for (Row h : header) h.createCell(c).setCellStyle(st.sumHeader);
            header[0].getCell(c).setCellValue(headers[c]);
            sheet.addMergedRegion(new CellRangeAddress(headerTop, headerTop + 2, c, c));
        }

        int first = headerTop + 3;
        for (int i = 0; i < months.size(); i++) {
            YearMonth ym = months.get(i);
            Ref ref = refs.get(ym);
            Row row = sheet.createRow(first + i);
            int x = first + i + 1;   // Excel row number
            row.setHeightInPoints(15f);

            text(row, 0, ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH), st.sumMonth);

            if (ref != null) formula(row, 1, ref.cell(exemptColumn), st.sumAmount);
            else number(row, 1, BigDecimal.ZERO, st.sumAmount);

            number(row, 2, BigDecimal.ZERO, st.sumAmount);   // Government sales / services: not tracked in the app

            if (ref != null) formula(row, 3, ref.cell(vatableColumn), st.sumAmount);
            else number(row, 3, BigDecimal.ZERO, st.sumAmount);

            formula(row, 4, "D" + x + "*0.12", st.sumAmount);
            formula(row, 5, "SUM(B" + x + ":E" + x + ")", st.sumAmount);
        }

        int totalIdx = first + months.size();
        Row total = sheet.createRow(totalIdx);
        text(total, 0, "Total   " + "- ".repeat(29).strip(), st.totalLabel);
        for (int c = 1; c <= 5; c++) {
            String col = CellReference.convertNumToColString(c);
            formula(total, c, "SUM(" + col + (first + 1) + ":" + col + totalIdx + ")", st.sumTotal);
        }
        return totalIdx;
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

    /** "LASTNAME, FIRSTNAME y MIDDLENAME", as on the quarterly sheet. */
    private String ownerLegalName() {
        Name n = taxpayer.getName();
        if (n == null) return "";
        String name = nzs(n.getLastName()).toUpperCase(Locale.ROOT) + ", "
                + nzs(n.getFirstName()).toUpperCase(Locale.ROOT);
        String middle = nzs(n.getMiddleName()).toUpperCase(Locale.ROOT);
        return middle.isEmpty() ? name : name + " y " + middle;
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

    private boolean isVisible(Kind kind, int col) {
        boolean[] v = visibleColumns.get(kind);
        return v == null || col >= v.length || v[col];
    }

    /** The column itself if visible, otherwise the nearest visible one (left first, then right). */
    private int anchor(Kind kind, int col) {
        boolean[] v = visibleColumns.get(kind);
        if (v == null || (col < v.length && v[col])) return col;
        for (int i = Math.min(col, v.length - 1); i >= 0; i--) if (v[i]) return i;
        for (int i = col + 1; i < v.length; i++) if (v[i]) return i;
        return col;
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
    // STYLES (fonts and formats copied from the original sheets)
    // ---------------------------------------------------------

    private static final class Styles {
        final CellStyle businessLine, boldLine, header, headerNumber, headerPercent;
        final CellStyle date, textLeft, textCenter, amount, totalLabel, totalAmount, plain;
        final CellStyle subLabel, subAmount;
        final CellStyle sumBanner, sumTitle, sumHeader, sumMonth, sumAmount, sumTotal, sumSign;

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
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER, false, false, null);
            subAmount = make(wb, "Calibri", 10, true, false,
                    HorizontalAlignment.RIGHT, VerticalAlignment.CENTER, false, false, ACCOUNTING);

            // Yearly / quarterly summary sheet
            sumBanner = make(wb, "Calibri", 12, true, false,
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER, false, false, null);
            sumTitle = make(wb, "Calibri", 12, true, true,
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER, false, false, null);
            sumHeader = make(wb, "Calibri", 10, false, false,
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER, true, true, null);
            sumMonth = make(wb, "Calibri", 10, false, false,
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER, false, true, null);
            sumAmount = make(wb, "Calibri", 10, false, false,
                    HorizontalAlignment.RIGHT, VerticalAlignment.CENTER, false, true, ACCOUNTING);
            sumTotal = make(wb, "Calibri", 11, true, true,
                    HorizontalAlignment.RIGHT, VerticalAlignment.CENTER, false, false, PESO);
            sumSign = make(wb, "Calibri", 12, true, false,
                    HorizontalAlignment.LEFT, VerticalAlignment.CENTER, false, false, null);
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