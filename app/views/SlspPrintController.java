package app.views;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.database.SlspRepository;
import app.models.Purchase;
import app.models.SLSP;
import app.models.Sale;
import app.models.Supplier;
import app.models.Taxpayer;
import app.viewmodels.SupplierViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;

import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
public class SlspPrintController {
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    @FXML private BorderPane root;
    @FXML private ComboBox<Integer> years;
    @FXML private ComboBox<String> quarters;
    @FXML private ToggleGroup rangeGroup;
    @FXML private RadioButton wholeYearRadio;
    @FXML private RadioButton quarterRadio;
    @FXML private CheckBox includeSales;
    @FXML private CheckBox includePurchases;
    @FXML private Text coverageText;
    @FXML private Text missingText;
    @FXML private Text previewSubtitle;
    @FXML private VBox previewPage;

    private SlspRepository repo;
    private final Map<Integer, String> supplierNames = new HashMap<>();
    private List<SLSP> loaded = List.of();

    @FXML
    private void initialize() throws Exception {
        Pages.bindShortcut(root, "ESC", this::back);
        Pages.bindShortcut(root, "Ctrl+P", this::printSlsp);
        Pages.bindShortcut(root, "Ctrl+E", this::exportExcel);
        repo = new SlspRepository();
        for (Supplier s : new SupplierViewModel().loadSuppliers()) {
            supplierNames.put(s.getId(), s.getTradeName());
        }

        int thisYear = LocalDate.now().getYear();
        years.getItems().setAll(thisYear, thisYear - 1);
        years.setValue(thisYear);

        quarters.getItems().setAll(
                "Q1 (Jan - Mar)", "Q2 (Apr - Jun)", "Q3 (Jul - Sep)", "Q4 (Oct - Dec)");
        quarters.getSelectionModel().select((LocalDate.now().getMonthValue() - 1) / 3);

        rangeGroup.selectedToggleProperty().addListener((_, _, _) -> {
            quarters.setDisable(!quarterRadio.isSelected());
            refreshPreview();
        });
        years.valueProperty().addListener((_, _, _) -> refreshPreview());
        quarters.valueProperty().addListener((_, _, _) -> refreshPreview());
        includeSales.selectedProperty().addListener((_, _, _) -> refreshPreview());
        includePurchases.selectedProperty().addListener((_, _, _) -> refreshPreview());

        refreshPreview();
    }

    // ---------- Range ----------

    private int firstMonth() {
        return quarterRadio.isSelected() ? quarters.getSelectionModel().getSelectedIndex() * 3 + 1 : 1;
    }

    private int lastMonth() {
        return quarterRadio.isSelected() ? firstMonth() + 2 : 12;
    }

    private String rangeLabel() {
        Integer y = years.getValue();
        return quarterRadio.isSelected()
                ? quarters.getValue() + " " + y
                : "January - December " + y;
    }

    // ---------- Preview ----------

    private void refreshPreview() {
        previewPage.getChildren().clear();
        Integer year = years.getValue();
        if (year == null || (quarterRadio.isSelected() && quarters.getSelectionModel().getSelectedIndex() < 0)) {
            previewPage.getChildren().add(new Text("Select a year and range to preview"));
            return;
        }

        try {
            String from = YearMonth.of(year, firstMonth()).toString();
            String to = YearMonth.of(year, lastMonth()).toString();
            loaded = repo.getSLSPsByTaxIdBetween(Taxpayer.getTaxpayer().getId(), from, to);
        } catch (Exception e) {
            e.printStackTrace();
            loaded = List.of();
        }

        int expected = lastMonth() - firstMonth() + 1;
        coverageText.setText(loaded.size() + " of " + expected + " months have an SLSP");
        missingText.setText(missingMonths(year));
        previewSubtitle.setText(rangeLabel());

        previewPage.getChildren().add(bold("SUMMARY LIST OF SALES AND PURCHASES", 16));
        previewPage.getChildren().add(new Text(
                Taxpayer.getTaxpayer().getTradeName() + "  |  TIN: " + Taxpayer.getTaxpayer().getTinNum()));
        previewPage.getChildren().add(new Text("For the period: " + rangeLabel()));

        if (loaded.isEmpty()) {
            previewPage.getChildren().add(new Text("\nNo SLSP records in this range."));
            return;
        }

        for (SLSP slsp : loaded) {
            previewPage.getChildren().add(bold("\n" + slsp.getTitle(), 13));
            if (includeSales.isSelected()) addSalesTable(slsp);
            if (includePurchases.isSelected()) addPurchasesTable(slsp);
        }
    }

    private String missingMonths(int year) {
        StringBuilder sb = new StringBuilder();
        for (int m = firstMonth(); m <= lastMonth(); m++) {
            String period = YearMonth.of(year, m).toString();
            boolean found = loaded.stream().anyMatch(s -> period.equals(s.getPeriod()));
            if (!found) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(YearMonth.of(year, m).getMonth().getDisplayName(
                        java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH));
            }
        }
        return sb.length() == 0 ? "" : "No SLSP for: " + sb;
    }

    private void addSalesTable(SLSP slsp) {
        previewPage.getChildren().add(bold("Sales", 11));
        GridPane grid = newGrid("Date", "SI No.", "Exempt", "Zero-rated", "Taxable", "Output VAT", "Gross");

        List<Sale> sales = new ArrayList<>(slsp.getSales());
        sales.sort(Comparator.comparing((Sale s) -> LocalDate.parse(s.getSaleDate()))
                .thenComparing(s -> String.valueOf(s.getSiNum())));

        BigDecimal ex = BigDecimal.ZERO, zr = BigDecimal.ZERO, tx = BigDecimal.ZERO,
                vat = BigDecimal.ZERO, gr = BigDecimal.ZERO;
        for (Sale s : sales) {
            ex = ex.add(s.getExemptAmount());
            zr = zr.add(s.getZeroRatedAmount());
            tx = tx.add(s.getTaxableAmount());
            vat = vat.add(s.getOutputVat());
            gr = gr.add(s.getGrossAmount());
        }

        String date = "", invoice = "";
        if (!sales.isEmpty()) {
            Sale first = sales.get(0), last = sales.get(sales.size() - 1);
            date = dateRange(first.getSaleDate(), last.getSaleDate());
            invoice = invoiceRange(first.getSiNum(), last.getSiNum());
        }

        addRow(grid, 1, date, invoice, ex, zr, tx, vat, gr);   // one summary row per month
        previewPage.getChildren().add(grid);
    }
    /** "January 01-31, 2026" (or "January 05, 2026" if both dates are the same day). */
    private String dateRange(String from, String to) {
        LocalDate a = LocalDate.parse(from), b = LocalDate.parse(to);
        String month = a.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
        return a.equals(b)
                ? "%s %02d, %d".formatted(month, a.getDayOfMonth(), a.getYear())
                : "%s %02d-%02d, %d".formatted(month, a.getDayOfMonth(), b.getDayOfMonth(), a.getYear());
    }

    /** First row's start number - last row's end number, e.g. "10001 - 19999". */
    private String invoiceRange(String firstRow, String lastRow) {
        String start = splitInvoice(firstRow, true);
        String end = splitInvoice(lastRow, false);
        if (start.isEmpty()) return end;
        if (end.isEmpty() || start.equals(end)) return start;
        return start + " - " + end;
    }

    private String splitInvoice(String value, boolean takeFirst) {
        if (value == null || value.isBlank()) return "";
        String[] parts = value.trim().split("\\s*-\\s*");
        return (takeFirst ? parts[0] : parts[parts.length - 1]).trim();
    }
    private void addPurchasesTable(SLSP slsp) {
        previewPage.getChildren().add(bold("Purchases", 11));
        GridPane grid = newGrid("Date", "Supplier / Invoice", "Exempt", "Zero-rated", "Taxable", "Input VAT", "Gross");
        BigDecimal ex = BigDecimal.ZERO, zr = BigDecimal.ZERO, tx = BigDecimal.ZERO,
                vat = BigDecimal.ZERO, gr = BigDecimal.ZERO;
        int row = 1;
        for (Purchase p : slsp.getPurchases()) {
            String who = supplierNames.getOrDefault(p.getSupplierId(), "?") + " / " + p.getInvoiceNum();
            addRow(grid, row++, p.getPurchaseDate(), who, p.getExemptAmount(), p.getZeroRatedAmount(),
                    p.getTaxableAmount(), p.getInputVat(), p.getGrossAmount());
            ex = ex.add(p.getExemptAmount());
            zr = zr.add(p.getZeroRatedAmount());
            tx = tx.add(p.getTaxableAmount());
            vat = vat.add(p.getInputVat());
            gr = gr.add(p.getGrossAmount());
        }
        addTotals(grid, row, ex, zr, tx, vat, gr);
        previewPage.getChildren().add(grid);
    }

    // ---------- Small layout helpers ----------

    private GridPane newGrid(String... headers) {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(2);
        for (int i = 0; i < headers.length; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(i == 1 ? Priority.ALWAYS : Priority.SOMETIMES);
            grid.getColumnConstraints().add(cc);
            grid.add(bold(headers[i], 10), i, 0);
        }
        return grid;
    }

    private void addRow(GridPane grid, int row, String date, String ref, BigDecimal... amounts) {
        grid.add(small(date), 0, row);
        grid.add(small(ref), 1, row);
        for (int i = 0; i < amounts.length; i++) grid.add(small(MONEY.format(amounts[i])), i + 2, row);
    }

    private void addTotals(GridPane grid, int row, BigDecimal... totals) {
        grid.add(bold("TOTAL", 10), 0, row);
        for (int i = 0; i < totals.length; i++) grid.add(bold(MONEY.format(totals[i]), 10), i + 2, row);
    }

    private Text bold(String s, double size) {
        Text t = new Text(s);
        t.setFont(Font.font("System", FontWeight.BOLD, size));
        return t;
    }

    private Text small(String s) {
        Text t = new Text(s == null ? "" : s);
        t.setFont(Font.font(10));
        return t;
    }

    // ---------- Actions ----------

    @FXML
    public void printSlsp(ActionEvent e) {
        if (loaded.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.WARNING, "There is nothing to print for this range.");
            a.setHeaderText(null);
            a.showAndWait();
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) return;
        if (!job.showPrintDialog(root.getScene().getWindow())) return;

        PageLayout layout = job.getPrinter().createPageLayout(
                Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT);

        double w = previewPage.getLayoutBounds().getWidth();
        double h = previewPage.getLayoutBounds().getHeight();
        double scale = Math.min(1.0, Math.min(
                layout.getPrintableWidth() / w,
                layout.getPrintableHeight() / h));

        double offsetX = (layout.getPrintableWidth() - w * scale) / 2.0;
        Translate move = new Translate(offsetX - previewPage.getLayoutX(), -previewPage.getLayoutY());
        Scale shrink = new Scale(scale, scale, 0, 0);

        String oldStyle = previewPage.getStyle();
        previewPage.setStyle("-fx-background-color: white;");   // no drop shadow
        previewPage.getTransforms().addAll(move, shrink);
        try {
            if (job.printPage(layout, previewPage)) job.endJob();
        } finally {
            previewPage.getTransforms().removeAll(move, shrink);
            previewPage.setStyle(oldStyle);
        }
    }
    private record XlStyles(CellStyle head, CellStyle date, CellStyle money,
            CellStyle bold, CellStyle moneyBold) {}
    @FXML
    public void exportExcel(ActionEvent e) {
        if (loaded.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.WARNING, "There is nothing to export for this range.");
            a.setHeaderText(null);
            a.showAndWait();
            return;
        }
        if (!includeSales.isSelected() && !includePurchases.isSelected()) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Select Sales and/or Purchases to export.");
            a.setHeaderText(null);
            a.showAndWait();
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Export to Excel");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"));
        fc.setInitialFileName("SLSP_" + rangeLabel().replaceAll("[^A-Za-z0-9]+", "_") + ".xlsx");
        File file = fc.showSaveDialog(root.getScene().getWindow());
        if (file == null) return;

        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = new FileOutputStream(file)) {
            XlStyles st = buildStyles(wb);

            if (includeSales.isSelected()) {
                List<Object[]> rows = new ArrayList<>();
                for (SLSP slsp : loaded) {
                    for (Sale s : slsp.getSales()) {
                        rows.add(new Object[] { slsp.getTitle(), LocalDate.parse(s.getSaleDate()), s.getSiNum(),
                                s.getExemptAmount(), s.getZeroRatedAmount(), s.getTaxableAmount(),
                                s.getOutputVat(), s.getGrossAmount() });
                    }
                }
                rows.sort(Comparator.comparing((Object[] a) -> (LocalDate) a[1])
                        .thenComparing(a -> String.valueOf(a[2])));
                writeSheet(wb, "Sales",
                        new String[] { "Period", "Date", "Sales Invoice No.", "Exempt", "Zero-rated",
                                "Taxable", "Output VAT", "Gross" },
                        rows, 3, st);
            }

            if (includePurchases.isSelected()) {
                List<Object[]> rows = new ArrayList<>();
                for (SLSP slsp : loaded) {
                    for (Purchase p : slsp.getPurchases()) {
                        rows.add(new Object[] { slsp.getTitle(), LocalDate.parse(p.getPurchaseDate()),
                                supplierNames.getOrDefault(p.getSupplierId(), "?"), p.getInvoiceNum(),
                                p.getExemptAmount(), p.getZeroRatedAmount(), p.getTaxableAmount(),
                                p.getInputVat(), p.getGrossAmount() });
                    }
                }
                rows.sort(Comparator.comparing((Object[] a) -> (LocalDate) a[1])
                        .thenComparing(a -> String.valueOf(a[3])));
                writeSheet(wb, "Purchases",
                        new String[] { "Period", "Date", "Supplier", "Invoice No.", "Exempt", "Zero-rated",
                                "Taxable", "Input VAT", "Gross" },
                        rows, 4, st);
            }

            wb.write(out);
        } catch (Exception ex) {
            ex.printStackTrace();
            Alert a = new Alert(Alert.AlertType.ERROR,
                    "Could not export the file. Make sure it isn't open in Excel.\n" + ex.getMessage());
            a.setHeaderText(null);
            a.showAndWait();
        }
    }

    private XlStyles buildStyles(XSSFWorkbook wb) {
        var dataFormat = wb.createDataFormat();

        org.apache.poi.ss.usermodel.Font boldFont = wb.createFont();
        boldFont.setBold(true);

        CellStyle head = wb.createCellStyle();
        head.setFont(boldFont);
        head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        head.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle date = wb.createCellStyle();
        date.setDataFormat(dataFormat.getFormat("mm/dd/yyyy"));

        CellStyle money = wb.createCellStyle();
        money.setDataFormat(dataFormat.getFormat("#,##0.00"));

        CellStyle bold = wb.createCellStyle();
        bold.setFont(boldFont);

        CellStyle moneyBold = wb.createCellStyle();
        moneyBold.setFont(boldFont);
        moneyBold.setDataFormat(dataFormat.getFormat("#,##0.00"));

        return new XlStyles(head, date, money, bold, moneyBold);
    }

    /** firstAmountCol = index of the first numeric column; every column from there on gets a SUM. */
    private void writeSheet(XSSFWorkbook wb, String name, String[] headers, List<Object[]> rows,
                            int firstAmountCol, XlStyles st) {
        Sheet sheet = wb.createSheet(name);

        Row headRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(st.head());
        }

        int r = 1;
        for (Object[] data : rows) {
            Row row = sheet.createRow(r++);
            for (int i = 0; i < data.length; i++) {
                Cell c = row.createCell(i);
                Object v = data[i];
                if (v instanceof BigDecimal b) {
                    c.setCellValue(b.doubleValue());
                    c.setCellStyle(st.money());
                } else if (v instanceof LocalDate d) {
                    c.setCellValue(d);
                    c.setCellStyle(st.date());
                } else {
                    c.setCellValue(v == null ? "" : v.toString());
                }
            }
        }

        Row total = sheet.createRow(r);
        Cell label = total.createCell(0);
        label.setCellValue("TOTAL");
        label.setCellStyle(st.bold());
        for (int i = firstAmountCol; i < headers.length; i++) {
            Cell c = total.createCell(i);
            if (rows.isEmpty()) {
                c.setCellValue(0);
            } else {
                String col = CellReference.convertNumToColString(i);
                c.setCellFormula("SUM(" + col + "2:" + col + r + ")");
            }
            c.setCellStyle(st.moneyBold());
        }

        sheet.createFreezePane(0, 1);   // keep the header visible while scrolling
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }
    @FXML
    public void back(ActionEvent e) {
        Pages.change(e, Pages.SLSP_MANAGER);
    }
}
