package app.views;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import app.importer.SlspExcelImporter;
import app.models.Purchase;
import app.models.Sale;
import app.models.Taxpayer;
import app.viewmodels.SlspImportViewModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;

public class SlspImportController {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    @FXML private TextField searchField;
    @FXML private Text tradeNameText;
    @FXML private ComboBox<String> months;
    @FXML private BorderPane root;
    @FXML private ProgressBar importProgressBar;
    @FXML private Button saveBtn, clearBtn;
    @FXML private GridPane saleFooter, purchaseFooter;

    private final BooleanProperty hasImport = new SimpleBooleanProperty(false);
    // ----- sales
    @FXML private Tab saleTab;
    @FXML private TableView<ImportSaleRow> sTable;
    @FXML private TableColumn<ImportSaleRow, Integer> sColNum;
    @FXML private TableColumn<ImportSaleRow, String> sColDate;
    @FXML private TableColumn<ImportSaleRow, String> sColInvoice;
    @FXML private TableColumn<ImportSaleRow, BigDecimal> sColExempt;
    @FXML private TableColumn<ImportSaleRow, BigDecimal> sColZeroRated;
    @FXML private TableColumn<ImportSaleRow, BigDecimal> sColTaxable;
    @FXML private TableColumn<ImportSaleRow, BigDecimal> sColOutputVat;
    @FXML private TableColumn<ImportSaleRow, BigDecimal> sColGross;
    @FXML private TableColumn<ImportSaleRow, Void> sColDelete;
    @FXML private Text saleTotalRows;
    @FXML private TextField saleExempt, saleZeroRated, saleTaxable, saleOutputVat, saleGross;
    
    // ----- purchases
    @FXML private Tab purchaseTab;
    @FXML private TableView<ImportPurchaseRow> pTable;
    @FXML private TableColumn<ImportPurchaseRow, Integer> pColNum;
    @FXML private TableColumn<ImportPurchaseRow, String> pColDate;
    @FXML private TableColumn<ImportPurchaseRow, String> pColSupplier;
    @FXML private TableColumn<ImportPurchaseRow, String> pColInvoice;
    @FXML private TableColumn<ImportPurchaseRow, BigDecimal> pColExempt;
    @FXML private TableColumn<ImportPurchaseRow, BigDecimal> pColZeroRated;
    @FXML private TableColumn<ImportPurchaseRow, BigDecimal> pColTaxable;
    @FXML private TableColumn<ImportPurchaseRow, BigDecimal> pColInputVat;
    @FXML private TableColumn<ImportPurchaseRow, BigDecimal> pColGross;       // Total Amt. of VAT
    @FXML private TableColumn<ImportPurchaseRow, BigDecimal> pColTotalAmt;    // Total Amt. of Purch.
    @FXML private TableColumn<ImportPurchaseRow, Void> pColDelete;
    @FXML private Text purchaseTotalRows;
    @FXML private TextField purchaseExempt, purchaseZeroRated, purchaseTaxable, inputVat,
            purchaseGross, purchaseTotalAmt;

    // ----- suppliers
    @FXML private Tab supplierTab;
    @FXML private TableView<ImportSupplierRow> supplierTable;
    @FXML private TableColumn<ImportSupplierRow, Integer> supplierNum;
    @FXML private TableColumn<ImportSupplierRow, String> tNum;
    @FXML private TableColumn<ImportSupplierRow, String> tName;
    @FXML private TableColumn<ImportSupplierRow, String> bAddress;
    @FXML private TableColumn<ImportSupplierRow, Void> supplierColDelete;

    private Taxpayer taxpayer;
    private SlspExcelImporter.Result parsed;
    private SlspImportViewModel.Plan plan;

    private final ObservableList<ImportSaleRow> saleRows = FXCollections.observableArrayList();
    private final ObservableList<ImportPurchaseRow> purchaseRows = FXCollections.observableArrayList();
    private final ObservableList<ImportSupplierRow> supplierRows = FXCollections.observableArrayList();
    private final FilteredList<ImportSaleRow> saleView = new FilteredList<>(saleRows, _ -> true);
    private final FilteredList<ImportPurchaseRow> purchaseView = new FilteredList<>(purchaseRows, _ -> true);
    private final FilteredList<ImportSupplierRow> supplierView = new FilteredList<>(supplierRows, _ -> true);

    private record Loaded(SlspExcelImporter.Result parsed, SlspImportViewModel.Plan plan) {}

    // ---------------------------------------------------------
    // INITIALIZATION
    // ---------------------------------------------------------

    @FXML
    private void initialize() {
        taxpayer = Taxpayer.getTaxpayer();
        tradeNameText.setText("Import Excel to Application: "
                + (taxpayer == null ? "" : taxpayer.getTradeName()));

        Pages.bindShortcut(root, "Ctrl+F", searchField);
        Pages.bindShortcut(root, "Ctrl+S", this::SaveSlsp);
        Pages.bindShortcut(root, "Ctrl+W", this::ClearSlsp);
        Pages.bindShortcut(root, "Ctrl+I", this::ImportSlsp);
        Pages.bindShortcut(root, "Esc", this::back);

        // sales columns
        numbering(sColNum);
        text(sColDate, ImportSaleRow::date);
        text(sColInvoice, ImportSaleRow::invoice);
        money(sColExempt, r -> r.model.getExemptAmount());
        money(sColZeroRated, r -> r.model.getZeroRatedAmount());
        money(sColTaxable, r -> r.model.getTaxableAmount());
        money(sColOutputVat, r -> r.model.getOutputVat());
        money(sColGross, r -> r.model.getGrossAmount());
        deleteColumn(sColDelete, this::deleteSale);

        // purchase columns
        numbering(pColNum);
        text(pColDate, ImportPurchaseRow::date);
        text(pColSupplier, ImportPurchaseRow::supplier);
        text(pColInvoice, ImportPurchaseRow::invoice);
        money(pColExempt, r -> r.model.getExemptAmount());
        money(pColZeroRated, r -> r.model.getZeroRatedAmount());
        money(pColTaxable, r -> r.model.getTaxableAmount());
        money(pColInputVat, r -> r.model.getInputVat());
        money(pColGross, ImportPurchaseRow::vatTotal);
        money(pColTotalAmt, r -> r.model.getGrossAmount());
        deleteColumn(pColDelete, this::deletePurchase);

        // supplier columns (read-only; deleting a supplier would leave its purchases behind)
        numbering(supplierNum);
        text(tNum, r -> r.parsed().tin());
        text(tName, r -> r.parsed().tradeName());
        text(bAddress, r -> r.parsed().address());
        supplierColDelete.setVisible(false);

        sTable.setEditable(false);
        pTable.setEditable(false);
        supplierTable.setEditable(false);
        sTable.setItems(saleView);
        pTable.setItems(purchaseView);
        supplierTable.setItems(supplierView);
        evenColumns(sTable);
        evenColumns(pTable);
        evenColumns(supplierTable);
        alignFooter(saleFooter, sTable);
        alignFooter(purchaseFooter, pTable);

        saveBtn.disableProperty().bind(hasImport.not());
        clearBtn.disableProperty().bind(hasImport.not());
        months.getSelectionModel().selectedIndexProperty().addListener((_, _, _) -> fillRows());
        searchField.textProperty().addListener((_, _, _) -> applyFilter());

        importProgressBar.setProgress(0);
        updateFooters();
    }

    // ---------------------------------------------------------
    // COLUMN HELPERS
    // ---------------------------------------------------------
    private void evenColumns(TableView<?> table) {
        for (TableColumn<?, ?> c : table.getColumns()) {
            c.setResizable(true);
            c.setMinWidth(40);
            c.setPrefWidth(100);
            c.setMaxWidth(Double.MAX_VALUE);
        }
    }
    private void alignFooter(GridPane footer, TableView<?> table) {
        footer.getColumnConstraints().clear();
        footer.setHgap(0);

        for (TableColumn<?, ?> col : table.getColumns()) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.minWidthProperty().bind(col.widthProperty());
            cc.prefWidthProperty().bind(col.widthProperty());
            cc.maxWidthProperty().bind(col.widthProperty());
            footer.getColumnConstraints().add(cc);
        }
        for (Node n : footer.getChildren()) {
            if (n instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        }
    }
    private <S> void numbering(TableColumn<S, Integer> col) {
        col.setCellValueFactory(_ -> new ReadOnlyObjectWrapper<>(0));
        col.setCellFactory(_ -> new TableCell<>() {
            @Override protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
            }
        });
    }

    private <S> void text(TableColumn<S, String> col, Function<S, String> f) {
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(f.apply(c.getValue())));
    }

    private <S> void money(TableColumn<S, BigDecimal> col, Function<S, BigDecimal> f) {
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(f.apply(c.getValue())));
        col.setCellFactory(_ -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : MONEY.format(v));
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
    }

    private <S> void deleteColumn(TableColumn<S, Void> col, Consumer<S> action) {
        col.setCellFactory(_ -> new TableCell<>() {
            private final Button button = new Button("\u2715");
            {
                button.setOnAction(_ -> action.accept(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : button);
            }
        });
    }

    // ---------------------------------------------------------
    // ROWS, FILTER, TOTALS
    // ---------------------------------------------------------

    private void showParsed(Loaded loaded) {
        parsed = loaded.parsed();
        plan = loaded.plan();

        List<String> labels = new ArrayList<>();
        labels.add("All months");
        for (var m : plan.months()) {
            labels.add(m.period() + (m.exists() ? "  (exists)" : "  (new)"));
        }
        months.getItems().setAll(labels);

        supplierRows.setAll(parsed.suppliers().stream().map(ImportSupplierRow::new).toList());
        months.getSelectionModel().select(0);
        fillRows();

        if (parsed.months().isEmpty() || !parsed.warnings().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (parsed.months().isEmpty()) sb.append("No SLSP data was found in this file.\n\n");
            if (!parsed.warnings().isEmpty()) {
                sb.append(parsed.warnings().size()).append(" warning(s):\n");
                sb.append(String.join("\n", parsed.warnings().stream().limit(10).toList()));
            }
            new Alert(Alert.AlertType.WARNING, sb.toString().trim()).showAndWait();
        }
    }

    private void fillRows() {
        List<ImportSaleRow> sales = new ArrayList<>();
        List<ImportPurchaseRow> purchases = new ArrayList<>();

        if (parsed != null) {
            int selected = months.getSelectionModel().getSelectedIndex();   // 0 / -1 = all months
            for (int i = 0; i < parsed.months().size(); i++) {
                if (selected > 0 && i != selected - 1) continue;
                var m = parsed.months().get(i);
                for (var s : m.sales()) sales.add(new ImportSaleRow(m, s));
                for (var p : m.purchases()) purchases.add(new ImportPurchaseRow(m, p));
            }
        }
        saleRows.setAll(sales);
        purchaseRows.setAll(purchases);
        applyFilter();
    }

    private void applyFilter() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        saleView.setPredicate(r -> q.isEmpty() || r.search().contains(q));
        purchaseView.setPredicate(r -> q.isEmpty() || r.search().contains(q));
        supplierView.setPredicate(r -> q.isEmpty() || r.search().contains(q));
        updateFooters();
    }

    private void deleteSale(ImportSaleRow row) {
        row.month().sales().remove(row.parsed());
        saleRows.remove(row);
        updateFooters();
    }

    private void deletePurchase(ImportPurchaseRow row) {
        row.month().purchases().remove(row.parsed());
        purchaseRows.remove(row);
        updateFooters();
    }

    private static String fmt(BigDecimal v) {
        return MONEY.format(v);
    }

    private void updateFooters() {
        BigDecimal ex = BigDecimal.ZERO, zr = BigDecimal.ZERO, tx = BigDecimal.ZERO,
                vat = BigDecimal.ZERO, gr = BigDecimal.ZERO;
        for (ImportSaleRow r : saleView) {
            ex = ex.add(r.model.getExemptAmount());
            zr = zr.add(r.model.getZeroRatedAmount());
            tx = tx.add(r.model.getTaxableAmount());
            vat = vat.add(r.model.getOutputVat());
            gr = gr.add(r.model.getGrossAmount());
        }
        saleTotalRows.setText("Total for " + saleView.size() + " rows");
        saleExempt.setText(fmt(ex));
        saleZeroRated.setText(fmt(zr));
        saleTaxable.setText(fmt(tx));
        saleOutputVat.setText(fmt(vat));
        saleGross.setText(fmt(gr));

        BigDecimal pex = BigDecimal.ZERO, pzr = BigDecimal.ZERO, ptx = BigDecimal.ZERO,
                pvat = BigDecimal.ZERO, pbase = BigDecimal.ZERO, pgr = BigDecimal.ZERO;
        for (ImportPurchaseRow r : purchaseView) {
            pex = pex.add(r.model.getExemptAmount());
            pzr = pzr.add(r.model.getZeroRatedAmount());
            ptx = ptx.add(r.model.getTaxableAmount());
            pvat = pvat.add(r.model.getInputVat());
            pbase = pbase.add(r.vatTotal());
            pgr = pgr.add(r.model.getGrossAmount());
        }
        purchaseTotalRows.setText("Total for " + purchaseView.size() + " rows");
        purchaseExempt.setText(fmt(pex));
        purchaseZeroRated.setText(fmt(pzr));
        purchaseTaxable.setText(fmt(ptx));
        inputVat.setText(fmt(pvat));
        purchaseGross.setText(fmt(pbase));
        purchaseTotalAmt.setText(fmt(pgr));
    }

    // ---------------------------------------------------------
    // ACTIONS
    // ---------------------------------------------------------

    private void busy(Task<?> task) {
        importProgressBar.progressProperty().bind(task.progressProperty());   // indeterminate while running
        Thread t = new Thread(task, "slsp-import");
        t.setDaemon(true);
        t.start();
    }

    private void idle(double progress) {
        importProgressBar.progressProperty().unbind();
        importProgressBar.setProgress(progress);
    }

    private void error(String title, Throwable ex) {
        ex.printStackTrace();
        new Alert(Alert.AlertType.ERROR, title + "\n\n" + ex.getMessage()).showAndWait();
    }

    @FXML
    public void ImportSlsp(ActionEvent e) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import SLSP from Excel");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx", "*.xls"));
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;

        Task<Loaded> task = new Task<>() {
            @Override protected Loaded call() throws Exception {
                SlspExcelImporter.Result result;
                try (InputStream in = new FileInputStream(file)) {
                    result = new SlspExcelImporter().read(in);
                }
                return new Loaded(result, new SlspImportViewModel().analyze(result));
            }
        };
        task.setOnSucceeded(_ -> {
            idle(1);
            showParsed(task.getValue());
        });
        task.setOnFailed(_ -> {
            idle(0);
            error("Could not read the file.", task.getException());
        });
        busy(task);
    }

    @FXML
    public void SaveSlsp(ActionEvent e) {
        boolean hasRows = parsed != null && parsed.months().stream()
                .anyMatch(m -> !m.sales().isEmpty() || !m.purchases().isEmpty());
        if (!hasRows) {
            new Alert(Alert.AlertType.WARNING, "Import an Excel file with at least one row first.").showAndWait();
            return;
        }

        SlspImportViewModel.Mode mode = SlspImportViewModel.Mode.MERGE;
        if (plan != null && plan.months().stream().anyMatch(SlspImportViewModel.MonthPlan::exists)) {
            ChoiceDialog<SlspImportViewModel.Mode> d =
                    new ChoiceDialog<>(mode, SlspImportViewModel.Mode.values());
            d.setTitle("Months already exist");
            d.setHeaderText("MERGE adds new rows only, REPLACE overwrites the month, SKIP leaves it alone.");
            var choice = d.showAndWait();
            if (choice.isEmpty()) return;
            mode = choice.get();
        }

        final SlspImportViewModel.Mode chosen = mode;
        final SlspExcelImporter.Result toSave = parsed;

        Task<SlspImportViewModel.Summary> task = new Task<>() {
            @Override protected SlspImportViewModel.Summary call() throws Exception {
                return new SlspImportViewModel().save(toSave, chosen);
            }
        };
        task.setOnSucceeded(_ -> {
            idle(1);
            var s = task.getValue();
            new Alert(Alert.AlertType.INFORMATION,
                    s.monthsCreated() + " month(s) created, " + s.monthsUpdated() + " updated, "
                            + s.monthsSkipped() + " skipped.\n" + s.salesAdded() + " sale(s) and "
                            + s.purchasesAdded() + " purchase(s) added, " + s.duplicatesSkipped()
                            + " duplicate(s) skipped, " + s.suppliersCreated()
                            + " supplier(s) created.").showAndWait();
            Pages.change(e, Pages.SLSP);
        });
        task.setOnFailed(_ -> {
            idle(0);
            error("Import failed.", task.getException());
        });
        busy(task);
    }

    @FXML
    public void ClearSlsp(ActionEvent e) {
        parsed = null;
        plan = null;
        months.getItems().clear();
        saleRows.clear();
        purchaseRows.clear();
        supplierRows.clear();
        searchField.clear();
        idle(0);
        updateFooters();
    }

    @FXML
    public void back(ActionEvent e) {
        Pages.change(e, Pages.SLSP);
    }
}

// ---------------------------------------------------------
// ROW MODELS
// ---------------------------------------------------------

class ImportSaleRow {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH);

    private final SlspExcelImporter.ParsedMonth month;
    private final SlspExcelImporter.ParsedSale parsed;
    final Sale model = new Sale();   // reused for the VAT maths, so the screen matches the saved data

    ImportSaleRow(SlspExcelImporter.ParsedMonth month, SlspExcelImporter.ParsedSale parsed) {
        this.month = month;
        this.parsed = parsed;
        model.setSaleDate(parsed.date().toString());
        model.setSiNum(parsed.invoiceNo());
        model.setExemptAmount(parsed.exempt());
        model.setZeroRatedAmount(parsed.zeroRated());
        model.setGrossAmount(parsed.gross());
    }

    SlspExcelImporter.ParsedMonth month() { return month; }
    SlspExcelImporter.ParsedSale parsed() { return parsed; }
    String date() { return DATE.format(parsed.date()); }
    String invoice() { return parsed.invoiceNo(); }

    String search() {
        return (date() + " " + invoice() + " " + parsed.exempt() + " " + parsed.zeroRated() + " "
                + parsed.gross() + " " + model.getTaxableAmount() + " " + model.getOutputVat())
                .toLowerCase(Locale.ROOT);
    }
}

class ImportPurchaseRow {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH);

    private final SlspExcelImporter.ParsedMonth month;
    private final SlspExcelImporter.ParsedPurchase parsed;
    final Purchase model = new Purchase();

    ImportPurchaseRow(SlspExcelImporter.ParsedMonth month, SlspExcelImporter.ParsedPurchase parsed) {
        this.month = month;
        this.parsed = parsed;
        model.setPurchaseDate(parsed.date().toString());
        model.setInvoiceNum(parsed.invoiceNo());
        model.setExemptAmount(parsed.exempt());
        model.setZeroRatedAmount(parsed.zeroRated());
        model.setGrossAmount(parsed.gross());
    }

    SlspExcelImporter.ParsedMonth month() { return month; }
    SlspExcelImporter.ParsedPurchase parsed() { return parsed; }
    String date() { return DATE.format(parsed.date()); }
    String invoice() { return parsed.invoiceNo(); }

    String supplier() {
        String name = parsed.tradeName() == null ? "" : parsed.tradeName();
        String tin = parsed.tin() == null ? "" : parsed.tin();
        return tin.isEmpty() ? name : name + ": " + tin;
    }

    /** Total Amt. of VAT = Total Amt. of Purchases - VAT Exempt - Zero-rated (never below zero). */
    BigDecimal vatTotal() {
        return parsed.gross().subtract(parsed.exempt()).subtract(parsed.zeroRated()).max(BigDecimal.ZERO);
    }

    String search() {
        return (date() + " " + invoice() + " " + supplier() + " " + parsed.address() + " "
                + parsed.exempt() + " " + parsed.zeroRated() + " " + parsed.gross())
                .toLowerCase(Locale.ROOT);
    }
}

class ImportSupplierRow {
    private final SlspExcelImporter.ParsedSupplier parsed;

    ImportSupplierRow(SlspExcelImporter.ParsedSupplier parsed) { this.parsed = parsed; }

    SlspExcelImporter.ParsedSupplier parsed() { return parsed; }

    String search() {
        return (parsed.tin() + " " + parsed.tradeName() + " " + parsed.address()).toLowerCase(Locale.ROOT);
    }
}