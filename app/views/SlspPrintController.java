package app.views;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.database.SlspRepository;
import app.models.Name;
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
import javafx.print.PrinterJob;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class SlspPrintController {

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00");

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

    private record Totals(
            BigDecimal taxable,
            BigDecimal vat,
            BigDecimal gross) {

        static Totals empty() {
            return new Totals(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }

        Totals add(
                BigDecimal taxableAmount,
                BigDecimal vatAmount,
                BigDecimal grossAmount) {

            return new Totals(
                    taxable.add(nz(taxableAmount)),
                    vat.add(nz(vatAmount)),
                    gross.add(nz(grossAmount))
            );
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ---------------------------------------------------------
    // INITIALIZATION
    // ---------------------------------------------------------

    @FXML
    private void initialize() throws Exception {

        Pages.bindShortcut(root, "ESC", this::back);
        Pages.bindShortcut(root, "Ctrl+P", this::printSlsp);
        Pages.bindShortcut(root, "Ctrl+E", this::exportExcel);

        repo = new SlspRepository();

        for (Supplier supplier : new SupplierViewModel().loadSuppliers()) {
            supplierNames.put(
                    supplier.getId(),
                    supplier.getTradeName()
            );
        }

        int currentYear = LocalDate.now().getYear();

        years.getItems().setAll(
                currentYear,
                currentYear - 1
        );

        years.setValue(currentYear);

        quarters.getItems().setAll(
                "Q1 (Jan - Mar)",
                "Q2 (Apr - Jun)",
                "Q3 (Jul - Sep)",
                "Q4 (Oct - Dec)"
        );

        quarters.getSelectionModel().select(
                (LocalDate.now().getMonthValue() - 1) / 3
        );

        quarters.setDisable(!quarterRadio.isSelected());

        rangeGroup.selectedToggleProperty().addListener(
        		(_,_,_) -> {
                    quarters.setDisable(!quarterRadio.isSelected());
                    refreshPreview();
                }
        );

        years.valueProperty().addListener(
        		(_,_,_) -> refreshPreview()
        );

        quarters.valueProperty().addListener(
        		(_,_,_) -> refreshPreview()
        );

        includeSales.selectedProperty().addListener(
        		(_,_,_) -> refreshPreview()
        );

        includePurchases.selectedProperty().addListener(
        		(_,_,_)-> refreshPreview()
        );

        refreshPreview();
    }

    // ---------------------------------------------------------
    // DATE RANGE
    // ---------------------------------------------------------

    private int firstMonth() {
        return quarterRadio.isSelected()
                ? quarters.getSelectionModel().getSelectedIndex() * 3 + 1
                : 1;
    }

    private int lastMonth() {
        return quarterRadio.isSelected()
                ? firstMonth() + 2
                : 12;
    }

    private String rangeLabel() {
        Integer year = years.getValue();

        if (year == null) {
            return "";
        }

        if (quarterRadio.isSelected()) {
            String quarter = quarters.getValue();

            return quarter == null
                    ? String.valueOf(year)
                    : quarter + " " + year;
        }

        return "January - December " + year;
    }

    private String getPeriodStart() {
        return YearMonth.of(
                years.getValue(),
                firstMonth()
        ).toString();
    }

    private String getPeriodEnd() {
        return YearMonth.of(
                years.getValue(),
                lastMonth()
        ).toString();
    }

    // ---------------------------------------------------------
    // PREVIEW
    // ---------------------------------------------------------

    private void refreshPreview() {

        previewPage.getChildren().clear();

        Integer year = years.getValue();

        if (year == null
                || (quarterRadio.isSelected()
                && quarters.getSelectionModel().getSelectedIndex() < 0)) {

            previewPage.getChildren().add(
                    new Text("Select a year and range to preview.")
            );

            return;
        }

        Taxpayer taxpayer = Taxpayer.getTaxpayer();

        if (taxpayer == null) {
            previewPage.getChildren().add(
                    new Text("No taxpayer is currently selected.")
            );

            loaded = List.of();
            return;
        }

        try {
            loaded = repo.getSLSPsByTaxIdBetween(
                    taxpayer.getId(),
                    getPeriodStart(),
                    getPeriodEnd()
            );

            if (loaded == null) {
                loaded = List.of();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            loaded = List.of();
        }

        int expectedMonths = lastMonth() - firstMonth() + 1;

        coverageText.setText(
                loaded.size() + " of " + expectedMonths
                        + " months have an SLSP"
        );

        missingText.setText(missingMonths(year));

        previewSubtitle.setText(rangeLabel());

        previewPage.getChildren().add(
                bold("SUMMARY LIST OF SALES AND PURCHASES", 16)
        );

        previewPage.getChildren().add(
                new Text(
                        taxpayer.getTradeName()
                                + " | TIN: " + taxpayer.getTinNum()
                )
        );

        previewPage.getChildren().add(
                new Text("For the period: " + rangeLabel())
        );

        if (loaded.isEmpty()) {
            previewPage.getChildren().add(
                    new Text("\nNo SLSP records in this range.")
            );

            return;
        }

        Totals salesTotals = Totals.empty();
        Totals purchaseTotals = Totals.empty();

        BigDecimal outputVatTotal = BigDecimal.ZERO;
        BigDecimal inputVatTotal = BigDecimal.ZERO;

        for (SLSP slsp : loaded) {

            previewPage.getChildren().add(
                    bold("\n" + slsp.getTitle(), 13)
            );

            if (includeSales.isSelected()) {

                Totals currentSales = sumSales(slsp);

                salesTotals = salesTotals.add(
                        currentSales.taxable(),
                        currentSales.vat(),
                        currentSales.gross()
                );

                outputVatTotal = outputVatTotal.add(
                        currentSales.vat()
                );

                addSalesTable(slsp);
            }

            if (includePurchases.isSelected()) {

                Totals currentPurchases = sumPurchases(slsp);

                purchaseTotals = purchaseTotals.add(
                        currentPurchases.taxable(),
                        currentPurchases.vat(),
                        currentPurchases.gross()
                );

                inputVatTotal = inputVatTotal.add(
                        currentPurchases.vat()
                );

                addPurchasesTable(slsp);
            }
        }

        addSummary(
                salesTotals,
                purchaseTotals,
                outputVatTotal,
                inputVatTotal
        );
    }

    private String missingMonths(int year) {

        StringBuilder missing = new StringBuilder();

        for (int month = firstMonth(); month <= lastMonth(); month++) {

            String period = YearMonth.of(year, month).toString();

            boolean found = loaded.stream()
                    .anyMatch(slsp -> period.equals(slsp.getPeriod()));

            if (!found) {

                if (missing.length() > 0) {
                    missing.append(", ");
                }

                missing.append(
                        YearMonth.of(year, month)
                                .getMonth()
                                .getDisplayName(
                                        java.time.format.TextStyle.SHORT,
                                        Locale.ENGLISH
                                )
                );
            }
        }

        return missing.length() == 0
                ? ""
                : "No SLSP for: " + missing;
    }

    // ---------------------------------------------------------
    // CALCULATE TOTALS
    // ---------------------------------------------------------

    private Totals sumSales(SLSP slsp) {

        Totals totals = Totals.empty();

        if (slsp.getSales() == null) {
            return totals;
        }

        for (Sale sale : slsp.getSales()) {

            totals = totals.add(
                    sale.getTaxableAmount(),
                    sale.getOutputVat(),
                    sale.getGrossAmount()
            );
        }

        return totals;
    }

    private Totals sumPurchases(SLSP slsp) {

        Totals totals = Totals.empty();

        if (slsp.getPurchases() == null) {
            return totals;
        }

        for (Purchase purchase : slsp.getPurchases()) {

            totals = totals.add(
                    purchase.getTaxableAmount(),
                    purchase.getInputVat(),
                    purchase.getGrossAmount()
            );
        }

        return totals;
    }

    // ---------------------------------------------------------
    // SALES TABLE
    // ---------------------------------------------------------

    private void addSalesTable(SLSP slsp) {

        previewPage.getChildren().add(
                bold("Sales", 11)
        );

        GridPane grid = newGrid(
                "Date",
                "SI No.",
                "Taxable",
                "Output VAT",
                "Gross"
        );

        List<Sale> sales = new ArrayList<>();

        if (slsp.getSales() != null) {
            sales.addAll(slsp.getSales());
        }

        sales.sort(
                Comparator
                        .comparing(
                                (Sale sale) -> LocalDate.parse(
                                        sale.getSaleDate()
                                )
                        )
                        .thenComparing(
                                sale -> String.valueOf(sale.getSiNum())
                        )
        );

        Totals totals = sumSales(slsp);

        String date = "";
        String invoice = "";

        if (!sales.isEmpty()) {

            Sale first = sales.get(0);
            Sale last = sales.get(sales.size() - 1);

            date = dateRange(
                    first.getSaleDate(),
                    last.getSaleDate()
            );

            invoice = invoiceRange(
                    first.getSiNum(),
                    last.getSiNum()
            );
        }

        addRow(
                grid,
                1,
                date,
                invoice,
                totals.taxable(),
                totals.vat(),
                totals.gross()
        );

        addTotals(
                grid,
                2,
                totals.taxable(),
                totals.vat(),
                totals.gross()
        );

        previewPage.getChildren().add(grid);
    }

    // ---------------------------------------------------------
    // PURCHASES TABLE
    // ---------------------------------------------------------

    private void addPurchasesTable(SLSP slsp) {

        previewPage.getChildren().add(
                bold("Purchases", 11)
        );

        GridPane grid = newGrid(
                "Date",
                "Supplier / Invoice",
                "Taxable",
                "Input VAT",
                "Gross"
        );

        Totals totals = Totals.empty();

        List<Purchase> purchases = new ArrayList<>();

        if (slsp.getPurchases() != null) {
            purchases.addAll(slsp.getPurchases());
        }

        purchases.sort(
                Comparator
                        .comparing(
                                (Purchase purchase) ->
                                        LocalDate.parse(
                                                purchase.getPurchaseDate()
                                        )
                        )
                        .thenComparing(
                                purchase ->
                                        String.valueOf(
                                                purchase.getInvoiceNum()
                                        )
                        )
        );

        int row = 1;

        for (Purchase purchase : purchases) {

            String supplier = supplierNames.getOrDefault(
                    purchase.getSupplierId(),
                    "Unknown Supplier"
            );

            String reference = supplier
                    + " / "
                    + (purchase.getInvoiceNum() == null
                    ? ""
                    : purchase.getInvoiceNum());

            addRow(
                    grid,
                    row++,
                    purchase.getPurchaseDate(),
                    reference,
                    nz(purchase.getTaxableAmount()),
                    nz(purchase.getInputVat()),
                    nz(purchase.getGrossAmount())
            );

            totals = totals.add(
                    purchase.getTaxableAmount(),
                    purchase.getInputVat(),
                    purchase.getGrossAmount()
            );
        }

        addTotals(
                grid,
                row,
                totals.taxable(),
                totals.vat(),
                totals.gross()
        );

        previewPage.getChildren().add(grid);
    }

    // ---------------------------------------------------------
    // FINAL SUMMARY
    // ---------------------------------------------------------

    private void addSummary(
            Totals sales,
            Totals purchases,
            BigDecimal outputVat,
            BigDecimal inputVat) {

        previewPage.getChildren().add(
                bold("\nSUMMARY", 13)
        );

        GridPane grid = newGrid(
                "Description",
                "Taxable",
                "VAT",
                "Gross"
        );

        int row = 1;

        if (includeSales.isSelected()) {
            addSummaryRow(
                    grid,
                    row++,
                    "Sales Subtotal",
                    sales
            );
        }

        if (includePurchases.isSelected()) {
            addSummaryRow(
                    grid,
                    row++,
                    "Purchases Subtotal",
                    purchases
            );
        }

        Totals grand = new Totals(
                sales.taxable().add(purchases.taxable()),
                outputVat.subtract(inputVat),
                sales.gross().add(purchases.gross())
        );

        grid.add(
                bold("GRAND TOTAL", 10),
                0,
                row
        );

        grid.add(
                bold(formatMoney(grand.taxable()), 10),
                1,
                row
        );

        grid.add(
                bold(formatMoney(grand.vat()), 10),
                2,
                row
        );

        grid.add(
                bold(formatMoney(grand.gross()), 10),
                3,
                row
        );

        row++;

        grid.add(
                bold("Output VAT", 10),
                0,
                row
        );

        grid.add(
                bold(formatMoney(outputVat), 10),
                1,
                row
        );

        row++;

        grid.add(
                bold("Input VAT", 10),
                0,
                row
        );

        grid.add(
                bold(formatMoney(inputVat), 10),
                1,
                row
        );

        row++;

        grid.add(
                bold("TAX DUE", 11),
                0,
                row
        );

        grid.add(
                bold(
                        formatMoney(outputVat.subtract(inputVat)),
                        11
                ),
                1,
                row
        );

        previewPage.getChildren().add(grid);
    }

    private void addSummaryRow(
            GridPane grid,
            int row,
            String label,
            Totals totals) {

        grid.add(
                bold(label, 10),
                0,
                row
        );

        grid.add(
                small(formatMoney(totals.taxable())),
                1,
                row
        );

        grid.add(
                small(formatMoney(totals.vat())),
                2,
                row
        );

        grid.add(
                small(formatMoney(totals.gross())),
                3,
                row
        );
    }

    // ---------------------------------------------------------
    // TABLE HELPERS
    // ---------------------------------------------------------

    private String dateRange(String from, String to) {

        LocalDate start = LocalDate.parse(from);
        LocalDate end = LocalDate.parse(to);

        String month = start.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL,
                Locale.ENGLISH
        );

        return start.equals(end)
                ? "%s %02d, %d".formatted(
                        month,
                        start.getDayOfMonth(),
                        start.getYear()
                )
                : "%s %02d-%02d, %d".formatted(
                        month,
                        start.getDayOfMonth(),
                        end.getDayOfMonth(),
                        start.getYear()
                );
    }

    private String invoiceRange(String firstRow, String lastRow) {

        String start = splitInvoice(firstRow, true);
        String end = splitInvoice(lastRow, false);

        if (start.isEmpty()) {
            return end;
        }

        if (end.isEmpty() || start.equals(end)) {
            return start;
        }

        return start + " - " + end;
    }

    private String splitInvoice(String value, boolean takeFirst) {

        if (value == null || value.isBlank()) {
            return "";
        }

        String[] parts = value.trim().split("\\s*-\\s*");

        return (
                takeFirst
                        ? parts[0]
                        : parts[parts.length - 1]
        ).trim();
    }

    private GridPane newGrid(String... headers) {

        GridPane grid = new GridPane();

        grid.setHgap(8);
        grid.setVgap(2);

        for (int i = 0; i < headers.length; i++) {

            ColumnConstraints constraints = new ColumnConstraints();

            constraints.setHgrow(
                    i == 1
                            ? Priority.ALWAYS
                            : Priority.SOMETIMES
            );

            grid.getColumnConstraints().add(constraints);

            grid.add(
                    bold(headers[i], 10),
                    i,
                    0
            );
        }

        return grid;
    }

    private void addRow(
            GridPane grid,
            int row,
            String date,
            String reference,
            BigDecimal... amounts) {

        grid.add(
                small(date),
                0,
                row
        );

        grid.add(
                small(reference),
                1,
                row
        );

        for (int i = 0; i < amounts.length; i++) {

            grid.add(
                    small(formatMoney(amounts[i])),
                    i + 2,
                    row
            );
        }
    }

    private void addTotals(
            GridPane grid,
            int row,
            BigDecimal... totals) {

        grid.add(
                bold("SUBTOTAL", 10),
                0,
                row
        );

        for (int i = 0; i < totals.length; i++) {

            grid.add(
                    bold(formatMoney(totals[i]), 10),
                    i + 2,
                    row
            );
        }
    }

    private static String formatMoney(BigDecimal amount) {
        return "₱ " + MONEY.format(nz(amount));
    }

    private Text bold(String value, double size) {

        Text text = new Text(value);

        text.setFont(
                Font.font(
                        "System",
                        FontWeight.BOLD,
                        size
                )
        );

        return text;
    }

    private Text small(String value) {

        Text text = new Text(
                value == null ? "" : value
        );

        text.setFont(Font.font(10));

        return text;
    }

    // ---------------------------------------------------------
    // PRINT
    // ---------------------------------------------------------

    @FXML
    public void printSlsp(ActionEvent event) {

        if (loaded.isEmpty()) {
            showWarning("There is nothing to print for this range.");
            return;
        }

        if (!includeSales.isSelected()
                && !includePurchases.isSelected()) {

            showWarning("Select Sales and/or Purchases to print.");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();

        if (job == null) {
            showWarning("No printer is available.");
            return;
        }

        if (!job.showPrintDialog(root.getScene().getWindow())) {
            return;
        }

        PageLayout layout = job.getPrinter().createPageLayout(
                Paper.A4,
                PageOrientation.PORTRAIT,
                javafx.print.Printer.MarginType.DEFAULT
        );

        double width = previewPage.getLayoutBounds().getWidth();
        double height = previewPage.getLayoutBounds().getHeight();

        if (width <= 0 || height <= 0) {
            showWarning("The preview is not ready to print.");
            return;
        }

        double scale = Math.min(
                1.0,
                Math.min(
                        layout.getPrintableWidth() / width,
                        layout.getPrintableHeight() / height
                )
        );

        double offsetX = (
                layout.getPrintableWidth() - width * scale
        ) / 2.0;

        Translate move = new Translate(
                offsetX - previewPage.getLayoutX(),
                -previewPage.getLayoutY()
        );

        Scale shrink = new Scale(scale, scale, 0, 0);

        String oldStyle = previewPage.getStyle();

        previewPage.setStyle("-fx-background-color: white;");
        previewPage.getTransforms().addAll(move, shrink);

        try {

            if (job.printPage(layout, previewPage)) {
                job.endJob();
            }

        } finally {

            previewPage.getTransforms().removeAll(move, shrink);
            previewPage.setStyle(oldStyle);
        }
    }

    // ---------------------------------------------------------
    // EXCEL STYLES
    // ---------------------------------------------------------

    private record XlStyles(
            CellStyle head,
            CellStyle date,
            CellStyle money,
            CellStyle bold,
            CellStyle moneyBold) {
    }

    private XlStyles buildStyles(XSSFWorkbook workbook) {

        var dataFormat = workbook.createDataFormat();

        org.apache.poi.ss.usermodel.Font boldFont =
                workbook.createFont();

        boldFont.setBold(true);

        CellStyle head = workbook.createCellStyle();

        head.setFont(boldFont);
        head.setFillForegroundColor(
                IndexedColors.GREY_25_PERCENT.getIndex()
        );
        head.setFillPattern(
                FillPatternType.SOLID_FOREGROUND
        );

        CellStyle date = workbook.createCellStyle();

        date.setDataFormat(
                dataFormat.getFormat("mm/dd/yyyy")
        );

        CellStyle money = workbook.createCellStyle();

        money.setDataFormat(
                dataFormat.getFormat(
                        "₱ #,##0.00;[Red]-₱ #,##0.00"
                )
        );

        CellStyle bold = workbook.createCellStyle();

        bold.setFont(boldFont);

        CellStyle moneyBold = workbook.createCellStyle();

        moneyBold.setFont(boldFont);

        moneyBold.setDataFormat(
                dataFormat.getFormat(
                        "₱ #,##0.00;[Red]-₱ #,##0.00"
                )
        );

        return new XlStyles(
                head,
                date,
                money,
                bold,
                moneyBold
        );
    }

    // ---------------------------------------------------------
    // EXPORT TO EXCEL
    // ---------------------------------------------------------

    @FXML
    public void exportExcel(ActionEvent event) {

        if (loaded.isEmpty()) {
            showWarning("There is nothing to export for this range.");
            return;
        }

        if (!includeSales.isSelected()
                && !includePurchases.isSelected()) {

            showWarning("Select Sales and/or Purchases to export.");
            return;
        }

        Integer selectedYear = years.getValue();

        if (selectedYear == null) {
            showWarning("Select a year before exporting.");
            return;
        }

        FileChooser chooser = new FileChooser();

        chooser.setTitle("Export SLSP to Excel");

        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Excel Workbook (*.xlsx)",
                        "*.xlsx"
                )
        );

        chooser.setInitialFileName(
                getExportFileName("SLSP", selectedYear, "xlsx")
        );

        File file = chooser.showSaveDialog(
                root.getScene().getWindow()
        );

        if (file == null) {
            return;
        }

        // Ensure the extension is present.
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            file = new File(file.getParentFile(), file.getName() + ".xlsx");
        }

        try (
                XSSFWorkbook workbook = new XSSFWorkbook();
                OutputStream out = new FileOutputStream(file)
        ) {

            XlStyles styles = buildStyles(workbook);

            if (includeSales.isSelected()) {
                exportSalesSheet(workbook, styles);
            }

            if (includePurchases.isSelected()) {
                exportPurchasesSheet(workbook, styles);
            }

            writeSummarySheet(workbook, styles);

            workbook.write(out);

            Alert alert = new Alert(
                    Alert.AlertType.INFORMATION,
                    "Excel export completed successfully.\n\n"
                            + file.getAbsolutePath()
            );

            alert.setHeaderText(null);
            setAlertIcon(alert);
            alert.showAndWait();

        } catch (Exception ex) {

            ex.printStackTrace();

            Alert alert = new Alert(
                    Alert.AlertType.ERROR,
                    "Could not export the file. Make sure it isn't open in Excel.\n\n"
                            + ex.getMessage()
            );
            setAlertIcon(alert);
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }

    private void exportSalesSheet(
            XSSFWorkbook workbook,
            XlStyles styles) {

        List<Object[]> rows = new ArrayList<>();

        for (SLSP slsp : loaded) {

            if (slsp.getSales() == null) {
                continue;
            }

            for (Sale sale : slsp.getSales()) {

                rows.add(new Object[] {
                        slsp.getTitle(),
                        LocalDate.parse(sale.getSaleDate()),
                        sale.getSiNum(),
                        nz(sale.getTaxableAmount()),
                        nz(sale.getOutputVat()),
                        nz(sale.getGrossAmount())
                });
            }
        }

        rows.sort(
                Comparator
                        .comparing(
                                (Object[] row) -> (LocalDate) row[1]
                        )
                        .thenComparing(
                                row -> String.valueOf(row[2])
                        )
        );

        writeSheet(
                workbook,
                "Sales",
                new String[] {
                        "Period",
                        "Date",
                        "Sales Invoice No.",
                        "Taxable",
                        "Output VAT",
                        "Gross"
                },
                rows,
                3,
                styles
        );
    }

    private void exportPurchasesSheet(
            XSSFWorkbook workbook,
            XlStyles styles) {

        List<Object[]> rows = new ArrayList<>();

        for (SLSP slsp : loaded) {

            if (slsp.getPurchases() == null) {
                continue;
            }

            for (Purchase purchase : slsp.getPurchases()) {

                rows.add(new Object[] {
                        slsp.getTitle(),
                        LocalDate.parse(purchase.getPurchaseDate()),
                        supplierNames.getOrDefault(
                                purchase.getSupplierId(),
                                "Unknown Supplier"
                        ),
                        purchase.getInvoiceNum(),
                        nz(purchase.getTaxableAmount()),
                        nz(purchase.getInputVat()),
                        nz(purchase.getGrossAmount())
                });
            }
        }

        rows.sort(
                Comparator
                        .comparing(
                                (Object[] row) -> (LocalDate) row[1]
                        )
                        .thenComparing(
                                row -> String.valueOf(row[3])
                        )
        );

        writeSheet(
                workbook,
                "Purchases",
                new String[] {
                        "Period",
                        "Date",
                        "Supplier",
                        "Invoice No.",
                        "Taxable",
                        "Input VAT",
                        "Gross"
                },
                rows,
                4,
                styles
        );
    }

    // ---------------------------------------------------------
    // SUMMARY WORKSHEET
    // ---------------------------------------------------------

    private void writeSummarySheet(
            XSSFWorkbook workbook,
            XlStyles styles) {

        Sheet sheet = workbook.createSheet("Summary");

        Row heading = sheet.createRow(0);

        Cell headingCell = heading.createCell(0);

        headingCell.setCellValue(
                "SUMMARY LIST OF SALES AND PURCHASES"
        );

        headingCell.setCellStyle(styles.head());

        Row period = sheet.createRow(1);

        period.createCell(0).setCellValue(rangeLabel());

        Taxpayer taxpayer = Taxpayer.getTaxpayer();

        Row taxpayerRow = sheet.createRow(2);

        taxpayerRow.createCell(0).setCellValue(
                taxpayer == null ? "" : taxpayer.getTradeName()
        );

        String[] columns = {
                "Description",
                "Taxable",
                "Output VAT",
                "Input VAT",
                "Gross",
                "Tax Due"
        };

        Row header = sheet.createRow(4);

        for (int i = 0; i < columns.length; i++) {

            Cell cell = header.createCell(i);

            cell.setCellValue(columns[i]);
            cell.setCellStyle(styles.head());
        }

        Totals sales = Totals.empty();
        Totals purchases = Totals.empty();

        for (SLSP slsp : loaded) {

            if (includeSales.isSelected()) {

                Totals current = sumSales(slsp);

                sales = sales.add(
                        current.taxable(),
                        current.vat(),
                        current.gross()
                );
            }

            if (includePurchases.isSelected()) {

                Totals current = sumPurchases(slsp);

                purchases = purchases.add(
                        current.taxable(),
                        current.vat(),
                        current.gross()
                );
            }
        }

        BigDecimal outputVat = includeSales.isSelected()
                ? sales.vat()
                : BigDecimal.ZERO;

        BigDecimal inputVat = includePurchases.isSelected()
                ? purchases.vat()
                : BigDecimal.ZERO;

        int rowIndex = 5;

        if (includeSales.isSelected()) {

            writeSummaryRow(
                    sheet,
                    rowIndex++,
                    "Sales Subtotal",
                    sales.taxable(),
                    sales.vat(),
                    BigDecimal.ZERO,
                    sales.gross(),
                    BigDecimal.ZERO,
                    styles
            );
        }

        if (includePurchases.isSelected()) {

            writeSummaryRow(
                    sheet,
                    rowIndex++,
                    "Purchases Subtotal",
                    purchases.taxable(),
                    BigDecimal.ZERO,
                    purchases.vat(),
                    purchases.gross(),
                    BigDecimal.ZERO,
                    styles
            );
        }

        Totals combined = new Totals(
                sales.taxable().add(purchases.taxable()),
                outputVat.subtract(inputVat),
                sales.gross().add(purchases.gross())
        );

        writeSummaryRow(
                sheet,
                rowIndex++,
                "GRAND TOTAL",
                combined.taxable(),
                outputVat,
                inputVat,
                combined.gross(),
                BigDecimal.ZERO,
                styles
        );

        writeSummaryRow(
                sheet,
                rowIndex,
                "TAX DUE",
                BigDecimal.ZERO,
                outputVat,
                inputVat,
                BigDecimal.ZERO,
                outputVat.subtract(inputVat),
                styles
        );

        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }

        sheet.createFreezePane(0, 5);
    }

    private void writeSummaryRow(
            Sheet sheet,
            int rowIndex,
            String label,
            BigDecimal taxable,
            BigDecimal outputVat,
            BigDecimal inputVat,
            BigDecimal gross,
            BigDecimal taxDue,
            XlStyles styles) {

        Row row = sheet.createRow(rowIndex);

        Cell description = row.createCell(0);

        description.setCellValue(label);
        description.setCellStyle(styles.bold());

        BigDecimal[] values = {
                taxable,
                outputVat,
                inputVat,
                gross,
                taxDue
        };

        for (int i = 0; i < values.length; i++) {

            Cell cell = row.createCell(i + 1);

            cell.setCellValue(nz(values[i]).doubleValue());
            cell.setCellStyle(styles.moneyBold());
        }
    }

    // ---------------------------------------------------------
    // GENERIC EXCEL SHEET
    // ---------------------------------------------------------

    private void writeSheet(
            XSSFWorkbook workbook,
            String name,
            String[] headers,
            List<Object[]> rows,
            int firstAmountCol,
            XlStyles styles) {

        Sheet sheet = workbook.createSheet(name);

        Row headerRow = sheet.createRow(0);

        for (int i = 0; i < headers.length; i++) {

            Cell cell = headerRow.createCell(i);

            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.head());
        }

        int rowIndex = 1;

        for (Object[] data : rows) {

            Row row = sheet.createRow(rowIndex++);

            for (int i = 0; i < data.length; i++) {

                Cell cell = row.createCell(i);
                Object value = data[i];

                if (value instanceof BigDecimal amount) {

                    cell.setCellValue(amount.doubleValue());
                    cell.setCellStyle(styles.money());

                } else if (value instanceof LocalDate date) {

                    cell.setCellValue(date);
                    cell.setCellStyle(styles.date());

                } else {

                    cell.setCellValue(
                            value == null ? "" : value.toString()
                    );
                }
            }
        }

        Row totalRow = sheet.createRow(rowIndex);

        Cell totalLabel = totalRow.createCell(0);

        totalLabel.setCellValue("GRAND TOTAL");
        totalLabel.setCellStyle(styles.bold());

        for (int column = firstAmountCol;
                column < headers.length;
                column++) {

            Cell cell = totalRow.createCell(column);

            if (rows.isEmpty()) {

                cell.setCellValue(0);

            } else {

                String columnName =
                        CellReference.convertNumToColString(column);

                cell.setCellFormula(
                        "SUM(" + columnName + "2:"
                                + columnName + rowIndex + ")"
                );
            }

            cell.setCellStyle(styles.moneyBold());
        }

        sheet.createFreezePane(0, 1);

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // ---------------------------------------------------------
    // TAXPAYER-BASED FILE NAMES
    // ---------------------------------------------------------

    private String getTaxpayerFilePrefix() {

        Taxpayer taxpayer = Taxpayer.getTaxpayer();

        if (taxpayer == null || taxpayer.getName() == null) {
            return "Taxpayer";
        }

        Name name = taxpayer.getName();

        String lastName = name.getLastName() == null
                ? ""
                : name.getLastName().trim();

        String firstName = name.getFirstName() == null
                ? ""
                : name.getFirstName().trim();

        String initial = firstName.isEmpty()
                ? ""
                : firstName.substring(0, 1).toUpperCase(Locale.ROOT);

        String prefix = (lastName + " " + initial).trim();

        prefix = prefix.replaceAll("[\\\\/:*?\"<>|]", "");

        return prefix.isBlank()
                ? "Taxpayer"
                : prefix;
    }

    private String getExportFileName(
            String reportType,
            int year,
            String extension) {

        return "%s %s Year %d.%s".formatted(
                getTaxpayerFilePrefix(),
                reportType,
                year,
                extension
        );
    }

    // ---------------------------------------------------------
    // ALERTS AND NAVIGATION
    // ---------------------------------------------------------

    private void showWarning(String message) {

        Alert alert = new Alert(
                Alert.AlertType.WARNING,
                message
        );

        alert.setHeaderText(null);
        setAlertIcon(alert);
        alert.showAndWait();
    }
    private void setAlertIcon(Alert alert) {
    	try {

            var iconStream = getClass().getResourceAsStream(
                    "/app/views/fxml/images/logo.png"
            );

            if (iconStream != null) {

                Stage stage = (Stage) alert.getDialogPane()
                        .getScene()
                        .getWindow();

                stage.getIcons().add(new Image(iconStream));
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    public void back(ActionEvent event) {
        Pages.change(event, Pages.SLSP);
    }
}