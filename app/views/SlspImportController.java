package app.views;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
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
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;

public class SlspImportController {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private static final Color OK_COLOR = Color.web("#16a34a");     // green
    private static final Color FAIL_COLOR = Color.web("#dc2626");   // red

    private static final String ADD_CSS = "black";     

    @FXML private TextField searchField;
    @FXML private Text tradeNameText;
    @FXML private Text statusText;
    @FXML private ComboBox<String> months;
    @FXML private BorderPane root;
    @FXML private ProgressBar importProgressBar;
    @FXML private Button saveBtn, clearBtn;
    @FXML private GridPane saleFooter, purchaseFooter;

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

    private final BooleanProperty hasImport = new SimpleBooleanProperty(false);

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
        numbering(sColNum, ImportSaleRow::css);
        textCol(sColDate, ImportSaleRow::date, ImportSaleRow::css, Pos.CENTER_LEFT);
        textCol(sColInvoice, ImportSaleRow::invoice, ImportSaleRow::css, Pos.CENTER_LEFT);
        moneyCol(sColExempt, r -> r.model.getExemptAmount(), ImportSaleRow::css);
        moneyCol(sColZeroRated, r -> r.model.getZeroRatedAmount(), ImportSaleRow::css);
        moneyCol(sColTaxable, r -> r.model.getTaxableAmount(), ImportSaleRow::css);
        moneyCol(sColOutputVat, r -> r.model.getOutputVat(), ImportSaleRow::css);
        moneyCol(sColGross, r -> r.model.getGrossAmount(), ImportSaleRow::css);
        deleteColumn(sColDelete, this::deleteSale);

        // purchase columns
        numbering(pColNum, ImportPurchaseRow::css);
        textCol(pColDate, ImportPurchaseRow::date, ImportPurchaseRow::css, Pos.CENTER_LEFT);
        textCol(pColSupplier, ImportPurchaseRow::supplier, ImportPurchaseRow::css, Pos.CENTER_LEFT);
        textCol(pColInvoice, ImportPurchaseRow::invoice, ImportPurchaseRow::css, Pos.CENTER_LEFT);
        moneyCol(pColExempt, r -> r.model.getExemptAmount(), ImportPurchaseRow::css);
        moneyCol(pColZeroRated, r -> r.model.getZeroRatedAmount(), ImportPurchaseRow::css);
        moneyCol(pColTaxable, r -> r.model.getTaxableAmount(), ImportPurchaseRow::css);
        moneyCol(pColInputVat, r -> r.model.getInputVat(), ImportPurchaseRow::css);
        moneyCol(pColGross, ImportPurchaseRow::vatTotal, ImportPurchaseRow::css);
        moneyCol(pColTotalAmt, r -> r.model.getGrossAmount(), ImportPurchaseRow::css);
        deleteColumn(pColDelete, this::deletePurchase);

        // supplier columns (read-only; deleting a supplier would leave its purchases behind)
        numbering(supplierNum, _ -> ADD_CSS);
        textCol(tNum, r -> r.parsed().tin(), _ -> ADD_CSS, Pos.CENTER_LEFT);
        textCol(tName, r -> r.parsed().tradeName(), _ -> ADD_CSS, Pos.CENTER_LEFT);
        textCol(bAddress, r -> r.parsed().address(), _ -> ADD_CSS, Pos.CENTER_LEFT);
        supplierColDelete.setVisible(false);

        sTable.setEditable(false);
        pTable.setEditable(false);
        supplierTable.setEditable(false);
        sTable.setItems(saleView);
        pTable.setItems(purchaseView);
        supplierTable.setItems(supplierView);

        evenColumns(sTable, sColNum, sColDelete);
        evenColumns(pTable, pColNum, pColDelete);
        evenColumns(supplierTable, supplierNum, supplierColDelete);
        alignFooter(saleFooter, sTable);
        alignFooter(purchaseFooter, pTable);

        saveBtn.disableProperty().bind(hasImport.not());
        clearBtn.disableProperty().bind(hasImport.not());

        months.getSelectionModel().selectedIndexProperty().addListener((_, _, _) -> fillRows());
        searchField.textProperty().addListener((_, _, _) -> applyFilter());

        importProgressBar.setProgress(0);
        clearStatus();
        updateFooters();
    }

    // ---------------------------------------------------------
    // STATUS
    // ---------------------------------------------------------

    private void setStatus(String message, boolean success) {
        statusText.setText(message);
        statusText.setFill(success ? OK_COLOR : FAIL_COLOR);
    }

    private void clearStatus() {
        statusText.setText("");
    }

    // ---------------------------------------------------------
    // COLUMN LAYOUT
    // ---------------------------------------------------------

    /** Every column starts with the same width; the constrained policy keeps them even as the table resizes. */
    private void evenColumns(TableView<?> table, TableColumn<?, ?>... fixed) {
        List<TableColumn<?, ?>> fixedCols = List.of(fixed);

        for (TableColumn<?, ?> c : table.getColumns()) {
            if (fixedCols.contains(c)) {
                c.setResizable(false);
                c.minWidthProperty().bind(table.widthProperty().multiply(0.010));
                c.prefWidthProperty().bind(table.widthProperty().multiply(0.010));
                c.maxWidthProperty().bind(table.widthProperty().multiply(0.010));
            } else {
                c.setResizable(true);
                c.setMinWidth(40);
                c.setPrefWidth(100);
                c.setMaxWidth(Double.MAX_VALUE);
            }
        }
    }
    /** Each footer column is bound to the width of the matching table column, so the totals line up. */
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

    // ---------------------------------------------------------
    // COLUMN HELPERS (read-only, copyable text fields)
    // ---------------------------------------------------------

    private static final String COPY_STYLE =
            "-fx-background-color: none; -fx-background-insets: 0; "
            + "-fx-background-radius: 0; -fx-padding: 0;";

    /** A non-editable text field: it can be selected and copied, but not changed. */
    private TextField newCopyField(Pos align) {
        TextField tf = new TextField();
        tf.setEditable(false);
        tf.setAlignment(align);
        tf.getStyleClass().add("copy-edit-text");
        URL css = getClass().getResource("/app/views/fxml/application.css");
        if (css != null) tf.getStylesheets().add(css.toExternalForm());
        return tf;
    }

    private static String fieldStyle(String color) {
        return COPY_STYLE + " -fx-text-fill: " + color + ";";
    }

    private <S> void numbering(TableColumn<S, Integer> col, Function<S, String> color) {
        col.setCellValueFactory(_ -> new ReadOnlyObjectWrapper<>(0));
        col.setCellFactory(_ -> new TableCell<S, Integer>() {
            private final TextField field = newCopyField(Pos.CENTER_LEFT);
            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                field.prefWidthProperty().bind(widthProperty());
            }
            @Override protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                S row = getTableRow() == null ? null : getTableRow().getItem();
                field.setText(String.valueOf(getIndex() + 1));
                field.setStyle(fieldStyle(row == null ? ADD_CSS : color.apply(row)));
                setGraphic(field);
            }
        });
    }

    private <S, T> void copyCells(TableColumn<S, T> col, Function<T, String> fmt,
                                  Function<S, String> color, Pos align) {
        col.setCellFactory(_ -> new TableCell<S, T>() {
            private final TextField field = newCopyField(align);
            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                field.prefWidthProperty().bind(widthProperty());
            }
            @Override protected void updateItem(T v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                S row = getTableRow() == null ? null : getTableRow().getItem();
                field.setText(fmt.apply(v));
                field.setStyle(fieldStyle(row == null ? ADD_CSS : color.apply(row)));
                setGraphic(field);
            }
        });
    }

    private <S> void textCol(TableColumn<S, String> col, Function<S, String> f,
                             Function<S, String> color, Pos align) {
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(f.apply(c.getValue())));
        copyCells(col, v -> v == null ? "" : v, color, align);
    }

    private <S> void moneyCol(TableColumn<S, BigDecimal> col, Function<S, BigDecimal> f,
                              Function<S, String> color) {
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(f.apply(c.getValue())));
        copyCells(col, v -> v == null ? "" : MONEY.format(v), color, Pos.CENTER_RIGHT);
    }

    private <S> void deleteColumn(TableColumn<S, Void> col, Consumer<S> action) {
        col.setCellFactory(_ -> new TableCell<>() {
            private final Button button = new Button("\u2715");
            {
            	button.setId("red-button");
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
        hasImport.set(!parsed.months().isEmpty());

        List<String> labels = new ArrayList<>();
        labels.add("All months");
        for (var m : plan.months()) {
            labels.add(m.period() + (m.exists() ? "  (exists)" : "  (new)"));
        }
        months.getItems().setAll(labels);

        supplierRows.setAll(parsed.suppliers().stream().map(ImportSupplierRow::new).toList());
        months.getSelectionModel().select(0);
        fillRows();

        String extra = parsed.warnings().isEmpty() ? ""
                : "  " + parsed.warnings().size() + " warning(s): " + parsed.warnings().get(0);

        if (parsed.months().isEmpty()) {
            setStatus("Import failed: no SLSP data found in this file." + extra, false);
            return;
        }

        int sales = parsed.months().stream().mapToInt(x -> x.sales().size()).sum();
        int purchases = parsed.months().stream().mapToInt(x -> x.purchases().size()).sum();
        int replacing = plan.replaceSales().size() + plan.replacePurchases().size();
        setStatus("Import complete: " + parsed.months().size() + " month(s), " + sales + " sale(s), "
                + purchases + " purchase(s) loaded"
                + (replacing > 0 ? ", " + replacing + " will replace existing rows" : "")
                + "." + extra, true);
    }

    private void fillRows() {
        List<ImportSaleRow> sales = new ArrayList<>();
        List<ImportPurchaseRow> purchases = new ArrayList<>();

        if (parsed != null) {
            int selected = months.getSelectionModel().getSelectedIndex();   // 0 / -1 = all months
            for (int i = 0; i < parsed.months().size(); i++) {
                if (selected > 0 && i != selected - 1) continue;
                var m = parsed.months().get(i);
                for (var s : m.sales()) {
                    sales.add(new ImportSaleRow(m, s, plan != null && plan.replaceSales().contains(s)));
                }
                for (var p : m.purchases()) {
                    purchases.add(new ImportPurchaseRow(m, p, plan != null && plan.replacePurchases().contains(p)));
                }
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

    private static String reason(Throwable ex) {
        if (ex == null) return "unknown error";
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    /** Clears the tables and the loaded data, but leaves the status message alone. */
    private void resetData() {
        parsed = null;
        plan = null;
        hasImport.set(false);
        months.getItems().clear();
        saleRows.clear();
        purchaseRows.clear();
        supplierRows.clear();
        searchField.clear();
        updateFooters();
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
            showParsed(task.getValue());   // sets the status (green, or red if nothing was found)
        });
        task.setOnFailed(_ -> {
            idle(0);
            task.getException().printStackTrace();
            setStatus("Import failed: " + reason(task.getException()), false);
        });
        busy(task);
    }

    @FXML
    public void SaveSlsp(ActionEvent e) {
        boolean hasRows = parsed != null && parsed.months().stream()
                .anyMatch(m -> !m.sales().isEmpty() || !m.purchases().isEmpty());
        if (!hasRows) {
            setStatus("Save failed: import an Excel file with at least one row first.", false);
            return;
        }

        final SlspExcelImporter.Result toSave = parsed;

        Task<SlspImportViewModel.Summary> task = new Task<>() {
            @Override protected SlspImportViewModel.Summary call() throws Exception {
                // MERGE: new rows are added, rows with the same date + invoice replace the existing ones
                return new SlspImportViewModel().save(toSave, SlspImportViewModel.Mode.MERGE);
            }
        };
        task.setOnSucceeded(_ -> {
            idle(1);
            var s = task.getValue();
            resetData();
            setStatus("Save complete: " + s.salesAdded() + " sale(s) and " + s.purchasesAdded()
                    + " purchase(s) added, " + s.duplicatesReplaced() + " duplicate(s) replaced, "
                    + s.suppliersCreated() + " supplier(s) created.", true);
        });
        task.setOnFailed(_ -> {
            idle(0);
            task.getException().printStackTrace();
            setStatus("Save failed: " + reason(task.getException()), false);
        });
        busy(task);
    }

    @FXML
    public void ClearSlsp(ActionEvent e) {
        resetData();
        idle(0);
        clearStatus();
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
    private final boolean replace;                 // true = replaces an existing row when saved
    final Sale model = new Sale();                 // reused for the VAT maths, so the screen matches the saved data

    ImportSaleRow(SlspExcelImporter.ParsedMonth month, SlspExcelImporter.ParsedSale parsed, boolean replace) {
        this.month = month;
        this.parsed = parsed;
        this.replace = replace;
        model.setSaleDate(parsed.date().toString());
        model.setSiNum(parsed.invoiceNo());
        model.setExemptAmount(parsed.exempt());
        model.setZeroRatedAmount(parsed.zeroRated());
        model.setGrossAmount(parsed.gross());
    }

    SlspExcelImporter.ParsedMonth month() { return month; }
    SlspExcelImporter.ParsedSale parsed() { return parsed; }
    String css() { return replace ? "orange" : "black"; }
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
    private final boolean replace;
    final Purchase model = new Purchase();

    ImportPurchaseRow(SlspExcelImporter.ParsedMonth month, SlspExcelImporter.ParsedPurchase parsed,
                      boolean replace) {
        this.month = month;
        this.parsed = parsed;
        this.replace = replace;
        model.setPurchaseDate(parsed.date().toString());
        model.setInvoiceNum(parsed.invoiceNo());
        model.setExemptAmount(parsed.exempt());
        model.setZeroRatedAmount(parsed.zeroRated());
        model.setGrossAmount(parsed.gross());
    }

    SlspExcelImporter.ParsedMonth month() { return month; }
    SlspExcelImporter.ParsedPurchase parsed() { return parsed; }
    String css() { return replace ? "orange" : "black"; }
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