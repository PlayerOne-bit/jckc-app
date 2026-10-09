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
        BigDecimal ex = BigDecimal.ZERO, zr = BigDecimal.ZERO, tx = BigDecimal.ZERO,
                vat = BigDecimal.ZERO, gr = BigDecimal.ZERO;
        int row = 1;
        for (Sale s : slsp.getSales()) {
            addRow(grid, row++, s.getSaleDate(), s.getSiNum(), s.getExemptAmount(), s.getZeroRatedAmount(),
                    s.getTaxableAmount(), s.getOutputVat(), s.getGrossAmount());
            ex = ex.add(s.getExemptAmount());
            zr = zr.add(s.getZeroRatedAmount());
            tx = tx.add(s.getTaxableAmount());
            vat = vat.add(s.getOutputVat());
            gr = gr.add(s.getGrossAmount());
        }
        addTotals(grid, row, ex, zr, tx, vat, gr);
        previewPage.getChildren().add(grid);
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

        // Landscape, scale the preview page down to the printable width
        PageLayout layout = job.getPrinter().createPageLayout(
                Paper.A4, PageOrientation.LANDSCAPE, Printer.MarginType.DEFAULT);
        double scale = Math.min(1.0, layout.getPrintableWidth() / previewPage.getBoundsInParent().getWidth());
        Scale s = new Scale(scale, scale);
        previewPage.getTransforms().add(s);
        try {
            if (job.printPage(layout, previewPage)) job.endJob();
        } finally {
            previewPage.getTransforms().remove(s);
        }
    }

    @FXML
    public void back(ActionEvent e) {
        Pages.change(e, Pages.SLSP_MANAGER);
    }
}
