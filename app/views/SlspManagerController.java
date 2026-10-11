package app.views;

import java.math.BigDecimal;
import java.math.RoundingMode;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javafx.scene.control.TableCell;
import app.models.Purchase;
import app.models.SLSP;
import app.models.Sale;
import app.models.Supplier;
import app.models.Taxpayer;
import app.viewmodels.SlspManagerViewModel;
import app.viewmodels.SupplierViewModel;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;

public class SlspManagerController {

    public static SLSP editingSlsp;

    @FXML private ComboBox<Integer> years;
    @FXML private ComboBox<String> months;
    @FXML private BorderPane root;
    @FXML private Button delBtn;
    @FXML private TextField sumInput;
    @FXML private TextField totalSum;
    @FXML private VBox sumContainer;
    @FXML private TabPane tabPane;
    @FXML private Text tradeNameText;

    // Sale table
    @FXML private TableView<SaleRow> sTable;
    @FXML private TableColumn<SaleRow, Integer> sColNum;
    @FXML private TableColumn<SaleRow, Integer> sColDate;
    @FXML private TableColumn<SaleRow, String> sColInvoice;
    @FXML private TableColumn<SaleRow, BigDecimal> sColExempt;
    @FXML private TableColumn<SaleRow, BigDecimal> sColZeroRated;
    @FXML private TableColumn<SaleRow, BigDecimal> sColTaxable;
    @FXML private TableColumn<SaleRow, BigDecimal> sColOutputVat;
    @FXML private TableColumn<SaleRow, BigDecimal> sColGross;
    @FXML private TableColumn<SaleRow, BigDecimal> sColDelete;
    @FXML private Button sAddBtn;
    @FXML private Text saleTotalRows;
    @FXML private TextField saleExempt;
    @FXML private TextField saleZeroRated;
    @FXML private TextField saleTaxable;
    @FXML private TextField outputVat;
    @FXML private TextField saleGross;
    @FXML private CheckBox sHideExempt, sHideZero;
    @FXML private GridPane saleFooter, purchaseFooter;

    // Purchase table
    @FXML private TableView<PurchaseRow> pTable;
    @FXML private TableColumn<PurchaseRow, Integer> pColNum;
    @FXML private TableColumn<PurchaseRow, Integer> pColDate;
    @FXML private TableColumn<PurchaseRow, Supplier> pColSupplier;
    @FXML private TableColumn<PurchaseRow, String> pColInvoice;
    @FXML private TableColumn<PurchaseRow, BigDecimal> pColExempt;
    @FXML private TableColumn<PurchaseRow, BigDecimal> pColZeroRated;
    @FXML private TableColumn<PurchaseRow, BigDecimal> pColTaxable;
    @FXML private TableColumn<PurchaseRow, BigDecimal> pColInputVat;
    @FXML private TableColumn<PurchaseRow, BigDecimal> pColGross;
    @FXML private TableColumn<PurchaseRow, BigDecimal> pColTotalAmt;
    @FXML private TableColumn<PurchaseRow, BigDecimal> pColDelete;

    @FXML private Button pAddBtn;
    @FXML private Text purchaseTotalRows;
    @FXML private TextField purchaseExempt;
    @FXML private TextField purchaseZeroRated;
    @FXML private TextField purchaseTaxable;
    @FXML private TextField inputVat;
    @FXML private TextField purchaseGross;
    @FXML private TextField purchaseTotalAmt;
    @FXML private CheckBox pHideExempt, pHideZero, pHideTotalAmt;

    private final ObservableList<SaleRow> saleRows = FXCollections.observableArrayList(
            r -> new Observable[] { r.dayProperty(), r.invoiceNoProperty(),
                    r.exemptProperty(), r.zeroRatedProperty(), r.grossInputProperty() });

    private final ObservableList<PurchaseRow> purchaseRows = FXCollections.observableArrayList(
            r -> new Observable[] { r.dayProperty(), r.supplierProperty(), r.invoiceNoProperty(),
                    r.exemptProperty(), r.zeroRatedProperty(), r.grossInputProperty() });

    // Used once when loading an existing SLSP (no live sorting, so new rows stay at the bottom)
 // SALES: Day of month -> Invoice number
    private static final Comparator<SaleRow> SALE_ORDER =
            Comparator.comparingInt(
                    (SaleRow r) -> r.dayProperty().get()
            ).thenComparing(
                    r -> r.invoiceNoProperty().get(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );

    // PURCHASES: Supplier name -> Day of month -> Invoice number
    private final Comparator<PurchaseRow> purchaseOrder =
            Comparator.comparing(
                    (PurchaseRow r) -> {
                        Supplier supplier = r.supplierProperty().get();

                        if (supplier == null || supplier.getTradeName() == null) {
                            return "";
                        }

                        return supplier.getTradeName().trim();
                    },
                    String.CASE_INSENSITIVE_ORDER
            ).thenComparingInt(
                    r -> r.dayProperty().get()
            ).thenComparing(
                    r -> r.invoiceNoProperty().get(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );

    private final ObservableList<Integer> dayOptions = FXCollections.observableArrayList();
    private ObservableList<Supplier> suppliers = FXCollections.observableArrayList();

    private List<BigDecimal> total = new java.util.ArrayList<>();

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");

    private SlspManagerViewModel vm;
    private boolean dirty = false;
    private boolean loading = false;
    private String baseTitle;

    // Undo history
    private static final int MAX_HISTORY = 200;
    private final Deque<Snapshot> undoStack = new ArrayDeque<>();
    private final Deque<Snapshot> redoStack = new ArrayDeque<>();
    private Snapshot lastSnapshot;          // the current state, as of the last recorded change
    private boolean historyPaused = false;  // true while restoring / making programmatic changes
    
 // Calculated (read-only) values get a tint so they stand apart from the cells you type into
    private static final String CALC_BG = "#eef2ff";
    private static final String CALC_TEXT = "#1e3a8a";
    private static final String CALC_CELL_STYLE =
            "-fx-background-color: " + CALC_BG + "; -fx-text-fill: " + CALC_TEXT + "; -fx-font-weight: bold;";
    private static final String CALC_FOOTER_STYLE =
            "-fx-background-color: " + CALC_BG + "; -fx-background-radius: 4; -fx-text-fill: " + CALC_TEXT + ";";

    @FXML
    private void initialize() throws Exception {
        Pages.bindShortcut(root, "Ctrl+S", this::SaveSlsp);
        Pages.bindShortcut(root, "Ctrl+D", this::DeleteSlsp);
        Pages.bindShortcut(root, "ESC", this::back);
        Pages.bindShortcut(sumInput, "Enter", this::sumInput);
        Pages.bindShortcut(sumInput, "Ctrl+Delete", this::clearSumInput);
        Pages.bindShortcut(root, "Ctrl+E", sumInput);
        Pages.bindShortcut(root, "Ctrl+B", this::Supplier);
        Pages.bindShortcut(root, "Ctrl+Enter", this::onAddRowShortcut);
        bindUndoShortcut();

        baseTitle = "SLSP : " + Taxpayer.getTaxpayer().getTradeName();
        tradeNameText.setText(baseTitle);

        vm = new SlspManagerViewModel();

        years.setItems(FXCollections.observableArrayList(LocalDate.now().getYear(), LocalDate.now().getYear() - 1));
        months.setItems(FXCollections.observableArrayList(
                "01 - January", "02 - February", "03 - March", "04 - April",
                "05 - May", "06 - June", "07 - July", "08 - August",
                "09 - September", "10 - October", "11 - November", "12 - December"));

        suppliers.setAll(new SupplierViewModel().loadSuppliers());

        formatPesoField(sumInput);
        formatPesoField(totalSum);
        totalSum.setEditable(false);
        setupSaleTable();
        setupPurchaseTable();
        alignFooter(saleFooter, sTable);
        alignFooter(purchaseFooter, pTable);
        styleCalculatedFooter(saleTaxable, outputVat, purchaseTaxable, inputVat, purchaseTotalAmt);
        sAddBtn.setOnAction(_ -> addPurchaseOrSaleRow(true));
        pAddBtn.setOnAction(_ -> addPurchaseOrSaleRow(false));

        // Hide / show columns (values are kept and still counted in the totals)
        bindHide(sHideExempt, sColExempt, saleExempt);
        bindHide(sHideZero, sColZeroRated, saleZeroRated);
        bindHide(pHideExempt, pColExempt, purchaseExempt);
        bindHide(pHideZero, pColZeroRated, purchaseZeroRated);
        bindHide(pHideTotalAmt, pColTotalAmt, purchaseTotalAmt);

        loading = true;
        if (editingSlsp != null) {
            loadExisting(editingSlsp);
        } else {
            YearMonth now = YearMonth.now();
            years.setValue(now.getYear());
            months.getSelectionModel().select(now.getMonthValue() - 1);
            delBtn.setVisible(false);
        }
        updateDayOptions();
        updateSaleTotals();
        updatePurchaseTotals();
        loading = false;

        lastSnapshot = captureSnapshot();   // baseline for undo

        saleRows.addListener((javafx.collections.ListChangeListener<SaleRow>) _ -> {
            markDirty();
            updateSaleTotals();
            recordHistory();
        });
        purchaseRows.addListener((javafx.collections.ListChangeListener<PurchaseRow>) _ -> {
            markDirty();
            updatePurchaseTotals();
            recordHistory();
        });
        years.valueProperty().addListener((_, _, _) -> onPeriodChanged());
        months.valueProperty().addListener((_, _, _) -> onPeriodChanged());
    }

    private void bindHide(CheckBox box, TableColumn<?, ?> col, TextField footer) {
        box.selectedProperty().addListener((_, _, hide) -> {
            col.setVisible(!hide);
            if (footer != null) footer.setVisible(!hide);
        });
    }

    private StringConverter<Integer> buildDayConverter() {
        return new StringConverter<>() {
            @Override public String toString(Integer day) {
                if (day == null) return "";
                Integer year = years.getValue();
                int monthIndex = months.getSelectionModel().getSelectedIndex();
                if (year == null || monthIndex < 0) return String.valueOf(day);
                return String.format("%02d/%02d/%04d", monthIndex + 1, day, year);
            }
            @Override public Integer fromString(String s) {
                if (s == null || s.isBlank()) return null;
                String[] parts = s.split("/");
                try {
                    return Integer.parseInt(parts[1]);
                } catch (Exception e) {
                    return null;
                }
            }
        };
    }

    // ---------- Loading an existing SLSP ----------

    private void loadExisting(SLSP slsp) throws Exception {
        vm.loadForPeriod(slsp.getPeriod());

        YearMonth ym = slsp.getYearMonth();

        years.setValue(ym.getYear());
        months.getSelectionModel().select(ym.getMonthValue() - 1);
        delBtn.setVisible(true);

        // Load sales.
        for (Sale sale : vm.getSales()) {
            SaleRow row = new SaleRow();

            row.dayProperty().set(
                    LocalDate.parse(sale.getSaleDate()).getDayOfMonth()
            );

            row.invoiceNoProperty().set(sale.getSiNum());
            row.exemptProperty().set(nz(sale.getExemptAmount()));
            row.zeroRatedProperty().set(nz(sale.getZeroRatedAmount()));
            row.grossProperty().set(nz(sale.getGrossAmount()));

            saleRows.add(row);
        }

        // Load purchases.
        for (Purchase purchase : vm.getPurchases()) {
            PurchaseRow row = new PurchaseRow();

            row.dayProperty().set(
                    LocalDate.parse(purchase.getPurchaseDate()).getDayOfMonth()
            );

            Supplier matchingSupplier = suppliers.stream()
                    .filter(s -> s.getId() == purchase.getSupplierId())
                    .findFirst()
                    .orElse(null);

            row.supplierProperty().set(matchingSupplier);
            row.invoiceNoProperty().set(purchase.getInvoiceNum());
            row.exemptProperty().set(nz(purchase.getExemptAmount()));
            row.zeroRatedProperty().set(nz(purchase.getZeroRatedAmount()));
            row.grossProperty().set(nz(purchase.getGrossAmount()));

            purchaseRows.add(row);
        }

        // Sort after loading all records.
        FXCollections.sort(saleRows, SALE_ORDER);
        FXCollections.sort(purchaseRows, purchaseOrder);
    }

    // ---------- Day-of-month restriction ----------

    private void updateDayOptions() {
        if (years.getValue() == null || months.getSelectionModel().getSelectedIndex() < 0) return;
        int year = years.getValue();
        int month = months.getSelectionModel().getSelectedIndex() + 1;
        int max = YearMonth.of(year, month).lengthOfMonth();

        List<Integer> days = new java.util.ArrayList<>();
        for (int d = 1; d <= max; d++) days.add(d);
        dayOptions.setAll(days);

        for (SaleRow r : saleRows) if (r.dayProperty().get() > max) r.dayProperty().set(max);
        for (PurchaseRow r : purchaseRows) if (r.dayProperty().get() > max) r.dayProperty().set(max);
    }

    // ---------- Totals ----------

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String formatMoney(BigDecimal v) {
        return "\u20B1 " + MONEY_FORMAT.format(nz(v));
    }

    private void updateSaleTotals() {
        BigDecimal exempt = BigDecimal.ZERO, zeroRated = BigDecimal.ZERO,
                taxable = BigDecimal.ZERO, vat = BigDecimal.ZERO, gross = BigDecimal.ZERO;
        int count = 0;
        for (SaleRow r : saleRows) {
            if (r.isEmpty()) continue;
            count++;
            exempt = exempt.add(nz(r.exemptProperty().get()));
            zeroRated = zeroRated.add(nz(r.zeroRatedProperty().get()));
            taxable = taxable.add(nz(r.taxableProperty().get()));
            vat = vat.add(r.getOutputVat());
            gross = gross.add(nz(r.grossInputProperty().get()));
        }
        if (saleTotalRows != null) saleTotalRows.setText("Total for " + count + " sale row" + (count == 1 ? ":" : "s:"));
        if (saleExempt != null) saleExempt.setText(formatMoney(exempt));
        if (saleZeroRated != null) saleZeroRated.setText(formatMoney(zeroRated));
        if (saleTaxable != null) saleTaxable.setText(formatMoney(taxable));
        if (outputVat != null) outputVat.setText(formatMoney(vat));
        if (saleGross != null) saleGross.setText(formatMoney(gross));
    }

    private void updatePurchaseTotals() {
        BigDecimal exempt = BigDecimal.ZERO, zeroRated = BigDecimal.ZERO,
                taxable = BigDecimal.ZERO, vat = BigDecimal.ZERO, gross = BigDecimal.ZERO,
                totalAmt = BigDecimal.ZERO;
        int count = 0;
        for (PurchaseRow r : purchaseRows) {
            if (r.isEmpty()) continue;
            count++;
            exempt = exempt.add(nz(r.exemptProperty().get()));
            zeroRated = zeroRated.add(nz(r.zeroRatedProperty().get()));
            taxable = taxable.add(nz(r.taxableProperty().get()));
            vat = vat.add(r.getInputVat());
            gross = gross.add(nz(r.grossInputProperty().get()));
            totalAmt = totalAmt.add(r.getTotalAmount());
        }
        if (purchaseTotalRows != null) purchaseTotalRows.setText("Total for " + count + " purchase row" + (count == 1 ? ":" : "s:"));
        if (purchaseExempt != null) purchaseExempt.setText(formatMoney(exempt));
        if (purchaseZeroRated != null) purchaseZeroRated.setText(formatMoney(zeroRated));
        if (purchaseTaxable != null) purchaseTaxable.setText(formatMoney(taxable));
        if (inputVat != null) inputVat.setText(formatMoney(vat));
        if (purchaseGross != null) purchaseGross.setText(formatMoney(gross));
        if (purchaseTotalAmt != null) purchaseTotalAmt.setText(formatMoney(totalAmt));
    }

    // ---------- Delete column ----------

    private <T, S> void wireDeleteColumn(TableColumn<T, S> col, TableView<T> table, ObservableList<T> rows) {
        col.setCellValueFactory(_ -> new ReadOnlyObjectWrapper<S>(null));
        col.setCellFactory(_ -> new TableCell<T, S>() {
            private final Button btn = new Button();
            {
                btn.setId("red-button");
                btn.getStylesheets().add(Pages.class.getResource("fxml/application.css").toExternalForm());
                FontIcon icon = new FontIcon("fas-trash");
                icon.setIconColor(Color.WHITE);
                btn.setGraphic(icon);
                btn.setFocusTraversable(false);
                btn.setOnAction(_ -> {
                    int i = getIndex();
                    if (i >= 0 && i < rows.size()) {
                        table.edit(-1, null);
                        rows.remove(i);
                    }
                });
                setAlignment(Pos.CENTER);
            }
            @Override protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }

    // ---------- Column widths: # and delete are fixed, visible columns share the rest evenly ----------

    private void setupColumnWidths(TableView<?> table, TableColumn<?, ?> numCol, TableColumn<?, ?> deleteCol) {
        final double numW = 45, delW = 55;
        Runnable relayout = () -> {
            int flexible = 0;
            for (TableColumn<?, ?> col : table.getColumns()) {
                if (col.isVisible() && col != numCol && col != deleteCol) flexible++;
            }
            double available = table.getWidth() - 2 - numW - delW;
            if (flexible == 0 || available <= 0) return;
            double each = available / flexible;
            for (TableColumn<?, ?> col : table.getColumns()) {
                col.setPrefWidth(col == numCol ? numW : col == deleteCol ? delW : each);
            }
        };
        table.widthProperty().addListener(_ -> relayout.run());
        for (TableColumn<?, ?> col : table.getColumns()) {
            col.visibleProperty().addListener(_ -> relayout.run());
        }
    }

    // ---------- Column wiring helpers ----------

    private static final StringConverter<BigDecimal> MONEY_CONVERTER = new StringConverter<>() {
        @Override public String toString(BigDecimal v) { return displayMoney(v); }
        @Override public BigDecimal fromString(String s) {
            try {
                if (s == null) return BigDecimal.ZERO;
                String clean = s.replace(",", "").replace("\u20B1", "").trim();
                if (clean.isEmpty()) return BigDecimal.ZERO;
                return new BigDecimal(clean).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }
    };
    private <T> void wireComputedColumn(
            TableColumn<T, BigDecimal> col,
            Function<T, BigDecimal> compute,
            Function<T, Observable[]> deps) {

        col.setCellValueFactory(cd ->
            Bindings.createObjectBinding(
                () -> compute.apply(cd.getValue()),
                deps.apply(cd.getValue())
            )
        );

        col.setCellFactory(_ -> new MoneyDisplayCell<>());
    }


    private <T> void wireRowNumberColumn(TableColumn<T, Integer> col) {
        col.setCellValueFactory(_ -> new ReadOnlyObjectWrapper<>(0));
        col.setCellFactory(_ -> new TableCell<>() {
            @Override protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
            }
        });
    }

    private <T> void wireDayColumn(
            TableColumn<T, Integer> col,
            Function<T, IntegerProperty> extractor) {

        col.setEditable(true);
        col.setCellValueFactory(cd ->
                extractor.apply(cd.getValue()).asObject());

        col.setCellFactory(_ ->
                new ComboBoxTableCell<T, Integer>(
                        buildDayConverter(), dayOptions) {

                    @Override
                    public void startEdit() {
                        super.startEdit();

                        if (getGraphic() instanceof ComboBox<?> combo) {
                            combo.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                                if (event.getCode() == KeyCode.ENTER
                                        && combo.isShowing()) {

                                    event.consume();

                                    Integer selected =
                                            (Integer) combo.getSelectionModel()
                                                    .getSelectedItem();

                                    if (selected != null) {
                                        commitEdit(selected);
                                    }
                                }
                            });
                        }
                    }
                });

        col.setOnEditCommit(event ->
                extractor.apply(event.getRowValue())
                        .set(event.getNewValue()));
    }

    // ---------- Sale table setup ----------

    private void setupSaleTable() {
        sTable.setItems(saleRows);
        setupColumnWidths(sTable, sColNum, sColDelete);
        wireDeleteColumn(sColDelete, sTable, saleRows);
        wireRowNumberColumn(sColNum);
        wireDayColumn(sColDate, SaleRow::dayProperty);

        sColInvoice.setEditable(true);
        sColInvoice.setCellValueFactory(cd -> cd.getValue().invoiceNoProperty());
        sColInvoice.setCellFactory(TextFieldTableCell.forTableColumn());
        sColInvoice.setOnEditCommit(ev -> ev.getRowValue().invoiceNoProperty().set(ev.getNewValue()));

        wireMoneyColumn(sColExempt, SaleRow::exemptProperty);                 // VAT Exempt [editable]
        wireMoneyColumn(sColZeroRated, SaleRow::zeroRatedProperty);           // Zero-rated [editable]
        wireComputedColumn(sColTaxable, r -> nz(r.taxableProperty().get()), SlspManagerController::saleDeps);
        wireComputedColumn(sColOutputVat, SaleRow::getOutputVat, SlspManagerController::saleDeps);
        wireMoneyColumn(sColGross, SaleRow::grossInputProperty);              // Total Amount [editable]

        setupCellNavigation(sTable,
                List.<TableColumn<SaleRow, ?>>of(sColDate, sColInvoice, sColExempt, sColZeroRated, sColGross),
                saleRows,
                () -> addPurchaseOrSaleRow(true));
    }

    // ---------- Purchase table setup ----------

    private void setupPurchaseTable() {
        pTable.setItems(purchaseRows);

        // Fixed-width row number and delete columns.
        setupColumnWidths(pTable, pColNum, pColDelete);

        // # column: displays 1, 2, 3, ...
        wireRowNumberColumn(pColNum);

        // Date dropdown.
        wireDayColumn(pColDate, PurchaseRow::dayProperty);

        // Supplier dropdown.
        pColSupplier.setEditable(true);
        pColSupplier.setCellValueFactory(
                cd -> cd.getValue().supplierProperty()
        );

        pColSupplier.setCellFactory(_ ->
        new ComboBoxTableCell<PurchaseRow, Supplier>(
                supplierConverter(), suppliers) {

            @Override
            public void startEdit() {
                super.startEdit();

                Node graphic = getGraphic();

                if (graphic instanceof ComboBox<?> combo) {
                    combo.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                        if (event.getCode() == KeyCode.ENTER && combo.isShowing()) {
                            event.consume();

                            Object selected = combo.getSelectionModel().getSelectedItem();

                            if (selected != null) {
                                Supplier supplier = (Supplier) selected;
                                commitEdit(supplier);
                            }
                        }
                    });
                }
            }
        }
);

        pColSupplier.setOnEditCommit(event ->
                event.getRowValue()
                        .supplierProperty()
                        .set(event.getNewValue())
        );

        // Invoice number.
        pColInvoice.setEditable(true);
        pColInvoice.setCellValueFactory(
                cd -> cd.getValue().invoiceNoProperty()
        );

        pColInvoice.setCellFactory(
                TextFieldTableCell.forTableColumn()
        );

        pColInvoice.setOnEditCommit(event ->
                event.getRowValue()
                        .invoiceNoProperty()
                        .set(event.getNewValue())
        );

        // Editable monetary columns.
        wireMoneyColumn(pColExempt, PurchaseRow::exemptProperty);
        wireMoneyColumn(pColZeroRated, PurchaseRow::zeroRatedProperty);
        wireMoneyColumn(pColGross, PurchaseRow::grossInputProperty);

        // Calculated columns.
        wireComputedColumn(
                pColTaxable,
                row -> nz(row.taxableProperty().get()),
                SlspManagerController::purchaseDeps
        );

        wireComputedColumn(
                pColInputVat,
                PurchaseRow::getInputVat,
                SlspManagerController::purchaseDeps
        );

        wireComputedColumn(
                pColTotalAmt,
                PurchaseRow::getTotalAmount,
                SlspManagerController::purchaseDeps
        );

        // Delete button.
        wireDeleteColumn(pColDelete, pTable, purchaseRows);

        // Keyboard navigation.
        setupCellNavigation(
                pTable,
                List.of(
                        pColDate,
                        pColSupplier,
                        pColInvoice,
                        pColExempt,
                        pColZeroRated,
                        pColGross
                ),
                purchaseRows,
                () -> addPurchaseOrSaleRow(false)
        );
    }

    // ---------- Keyboard navigation: Enter = new bottom row, Tab / Shift+Tab = next / previous cell ----------
    private boolean isEditingComboBoxShowing(TableView<?> table) {
        TablePosition<?, ?> editing = table.getEditingCell();

        if (editing == null) {
            return false;
        }

        for (Node node : table.lookupAll(".combo-box")) {
            if (node instanceof ComboBox<?> comboBox
                    && comboBox.isShowing()
                    && isDescendantOfEditingCell(comboBox, table, editing)) {
                return true;
            }
        }

        return false;
    }

    private boolean isDescendantOfEditingCell(
            Node node,
            TableView<?> table,
            TablePosition<?, ?> editing) {

        Node current = node;

        while (current != null && current != table) {
            if (current instanceof TableCell<?, ?> cell) {
                return cell.getIndex() == editing.getRow()
                        && cell.getTableColumn() == editing.getTableColumn();
            }

            current = current.getParent();
        }

        return false;
    }
    private <T> void setupCellNavigation(
            TableView<T> table,
            List<TableColumn<T, ?>> editableCols,
            ObservableList<T> rows,
            Runnable addRow) {

        table.setEditable(true);

        table.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            KeyCode code = ev.getCode();
            if (ev.isControlDown() && ev.getCode() == KeyCode.W) {
                ev.consume();

                int selectedIndex = table.getSelectionModel().getSelectedIndex();

                if (selectedIndex >= 0) {
                    table.getItems().remove(selectedIndex);
                }

                return;
            }
            // Allow the ComboBox to handle Enter before table navigation.
            if (code == KeyCode.ENTER
                    && table.getEditingCell() != null
                    && table.getEditingCell().getTableColumn() != null) {

                TableColumn<?, ?> editingColumn =
                        table.getEditingCell().getTableColumn();

                boolean dropdownColumn =
                        editingColumn == sColDate
                        || editingColumn == pColDate
                        || editingColumn == pColSupplier;

                if (dropdownColumn) {
                    Node focusOwner = table.getScene().getFocusOwner();

                    if (focusOwner instanceof ComboBox<?> combo && combo.isShowing()) {
                        return;
                    }
                }
            }
            /*
             * IMPORTANT:
             * Let an open ComboBox process Enter first.
             * Do not consume the event or navigate to another row.
             */
            if (code == KeyCode.ENTER && isEditingComboBoxShowing(table)) {
                return;
            }

            List<TableColumn<T, ?>> cols = editableCols.stream()
                    .filter(TableColumn::isVisible)
                    .toList();

            if (cols.isEmpty()) {
                return;
            }

            // Delete the selected row when no cell is being edited.
            if (code == KeyCode.DELETE && table.getEditingCell() == null) {
                int selected = table.getSelectionModel().getSelectedIndex();

                if (selected >= 0 && selected < rows.size()) {
                    rows.remove(selected);

                    if (!rows.isEmpty()) {
                        table.getSelectionModel()
                                .select(Math.min(selected, rows.size() - 1));
                    }
                }

                ev.consume();
                return;
            }

            /*
             * UP / DOWN while editing a text field.
             */
            if ((code == KeyCode.UP || code == KeyCode.DOWN)
                    && table.getEditingCell() != null
                    && ev.getTarget() instanceof TextField textField) {

                TablePosition<T, ?> editing = table.getEditingCell();
                int colIndex = cols.indexOf(editing.getTableColumn());

                if (colIndex >= 0) {
                    textField.fireEvent(new ActionEvent(textField, textField));

                    int targetRow = editing.getRow()
                            + (code == KeyCode.DOWN ? 1 : -1);

                    if (targetRow < 0) {
                        targetRow = 0;
                    }

                    if (targetRow >= rows.size()) {
                        addRow.run();
                        targetRow = table.getSelectionModel().getSelectedIndex();
                    }

                    final int r = Math.max(targetRow, 0);
                    final int c = colIndex;

                    ev.consume();

                    Platform.runLater(() -> {
                        if (r < table.getItems().size()) {
                            table.getSelectionModel().clearAndSelect(r);
                            table.scrollTo(r);
                            table.edit(r, cols.get(c));
                        }
                    });

                    return;
                }
            }

            // DOWN on the last row adds a row when not editing.
            if (code == KeyCode.DOWN && table.getEditingCell() == null) {
                int selected = table.getSelectionModel().getSelectedIndex();

                if (selected == rows.size() - 1) {
                    addRow.run();
                    ev.consume();
                }

                return;
            }

            if (code != KeyCode.TAB && code != KeyCode.ENTER) {
                return;
            }

            if (ev.isControlDown() || ev.isAltDown()) {
                return;
            }

            TablePosition<T, ?> editing = table.getEditingCell();

            int row = editing != null
                    ? editing.getRow()
                    : table.getSelectionModel().getSelectedIndex();

            if (row < 0) {
                return;
            }

            int colIndex = editing != null
                    ? cols.indexOf(editing.getTableColumn())
                    : -1;

            // Commit text-field edits before navigating.
            if (ev.getTarget() instanceof TextField textField) {
                textField.fireEvent(new ActionEvent(textField, textField));
            }
            
            int targetRow = row;
            int targetCol;

            if (code == KeyCode.ENTER) {
                // Enter moves down within the same column.
                targetCol = colIndex < 0 ? 0 : colIndex;
                targetRow = row + 1;

                if (targetRow >= rows.size()) {
                    addRow.run();
                    targetRow = table.getSelectionModel().getSelectedIndex();
                }
            } else {
                // Tab moves horizontally; Shift+Tab moves backward.
                int step = ev.isShiftDown() ? -1 : 1;

                targetCol = colIndex < 0
                        ? (step > 0 ? 0 : cols.size() - 1)
                        : colIndex + step;

                if (targetCol >= cols.size()) {
                    targetCol = 0;
                    targetRow++;
                } else if (targetCol < 0) {
                    targetCol = cols.size() - 1;
                    targetRow--;
                }

                if (targetRow < 0) {
                    targetRow = 0;
                    targetCol = 0;
                }

                if (targetRow >= rows.size()) {
                    addRow.run();
                    targetRow = table.getSelectionModel().getSelectedIndex();
                    targetCol = 0;
                }
            }

            if (targetRow < 0 || targetRow >= rows.size()) {
                return;
            }

            final int r = targetRow;
            final int c = Math.max(0, Math.min(targetCol, cols.size() - 1));

            ev.consume();

            Platform.runLater(() -> {
                if (r < table.getItems().size()) {
                    table.getSelectionModel().clearAndSelect(r);
                    table.scrollTo(r);
                    table.edit(r, cols.get(c));
                }
            });
        });
    }

    // ---------- Row creation: guarded, no duplicate blank rows, always at the bottom ----------

    private void onAddRowShortcut(ActionEvent e) {
        boolean isSaleTab = tabPane.getSelectionModel().getSelectedIndex() == 0;

        if (isSaleTab) {
            moveDownOrAddRow(sTable, saleRows, () -> addPurchaseOrSaleRow(true));
        } else {
            moveDownOrAddRow(pTable, purchaseRows, () -> addPurchaseOrSaleRow(false));
        }
    }
    private <T> void moveDownOrAddRow(
            TableView<T> table,
            ObservableList<T> rows,
            Runnable addRow) {

        int selectedRow = table.getSelectionModel().getSelectedIndex();

        if (selectedRow < 0) {
            if (rows.isEmpty()) {
                addRow.run();
            } else {
                table.getSelectionModel().select(0);
                table.scrollTo(0);
            }
            return;
        }

        if (selectedRow >= rows.size() - 1) {
            addRow.run();
            return;
        }

        int nextRow = selectedRow + 1;

        table.getSelectionModel().clearAndSelect(nextRow);
        table.scrollTo(nextRow);
    }

    private void addPurchaseOrSaleRow(boolean isSale) {
        if (isSale) {
            SaleRow existingBlank = saleRows.stream().filter(SaleRow::isEmpty).findFirst().orElse(null);
            if (existingBlank != null) {
                selectSaleRow(existingBlank);
                return;
            }
            SaleRow row = new SaleRow();
            int prevDay = saleRows.isEmpty() ? 1 : saleRows.get(saleRows.size() - 1).dayProperty().get();
            row.dayProperty().set(Math.max(1, prevDay));
            saleRows.add(row);
            selectSaleRow(row);
        } else {
            PurchaseRow existingBlank = purchaseRows.stream().filter(PurchaseRow::isEmpty).findFirst().orElse(null);
            if (existingBlank != null) {
                selectPurchaseRow(existingBlank);
                return;
            }
            PurchaseRow row = new PurchaseRow();
            if (purchaseRows.isEmpty()) {
                row.dayProperty().set(1);
            } else {
                PurchaseRow prev = purchaseRows.get(purchaseRows.size() - 1);
                row.dayProperty().set(Math.max(1, prev.dayProperty().get()));
                row.supplierProperty().set(prev.supplierProperty().get());
            }
            purchaseRows.add(row);
            selectPurchaseRow(row);
        }
    }

    private void selectSaleRow(SaleRow row) {
        sTable.getSelectionModel().select(row);
        int idx = saleRows.indexOf(row);
        if (idx >= 0) sTable.scrollTo(idx);
    }

    private void selectPurchaseRow(PurchaseRow row) {
        pTable.getSelectionModel().select(row);
        int idx = purchaseRows.indexOf(row);
        if (idx >= 0) pTable.scrollTo(idx);
    }

    // ---------- Undo (Ctrl+Z) ----------

    private record SaleState(int day, String invoice, BigDecimal exempt,
                             BigDecimal zeroRated, BigDecimal gross) {}

    private record PurchaseState(int day, Supplier supplier, String invoice, BigDecimal exempt,
                                 BigDecimal zeroRated, BigDecimal gross) {}

    private record Snapshot(Integer year, int monthIndex,
                            List<SaleState> sales, List<PurchaseState> purchases) {}

    private void bindUndoShortcut() {
        KeyCombination undoKeys = KeyCombination.valueOf("Ctrl+Z");
        KeyCombination redoKeys = KeyCombination.valueOf("Ctrl+Y");
        KeyCombination redoAltKeys = KeyCombination.valueOf("Ctrl+Shift+Z");
        root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            boolean isUndo = undoKeys.match(ev);
            boolean isRedo = redoKeys.match(ev) || redoAltKeys.match(ev);
            if (!isUndo && !isRedo) return;
            Node focused = root.getScene() == null ? null : root.getScene().getFocusOwner();
            // While typing in a text field, let it undo/redo its own text
            if (focused instanceof TextInputControl) return;
            if (isUndo) undo(); else redo();
            ev.consume();
        });
    }

    private Snapshot captureSnapshot() {
        List<SaleState> sales = new java.util.ArrayList<>();
        for (SaleRow r : saleRows) {
            sales.add(new SaleState(r.dayProperty().get(), r.invoiceNoProperty().get(),
                    r.exemptProperty().get(), r.zeroRatedProperty().get(), r.grossInputProperty().get()));
        }
        List<PurchaseState> purchases = new java.util.ArrayList<>();
        for (PurchaseRow r : purchaseRows) {
            purchases.add(new PurchaseState(r.dayProperty().get(), r.supplierProperty().get(),
                    r.invoiceNoProperty().get(), r.exemptProperty().get(),
                    r.zeroRatedProperty().get(), r.grossInputProperty().get()));
        }
        return new Snapshot(years.getValue(), months.getSelectionModel().getSelectedIndex(), sales, purchases);
    }
    private static String displayMoney(BigDecimal v) {
        return MONEY_FORMAT.format(nz(v).setScale(2, RoundingMode.HALF_UP));
    }
    
    /** Called after every change: pushes the previous state onto the undo stack. */
    private void recordHistory() {
        if (loading || historyPaused || lastSnapshot == null) return;
        Snapshot now = captureSnapshot();
        if (now.equals(lastSnapshot)) return;   // nothing actually changed
        undoStack.push(lastSnapshot);
        if (undoStack.size() > MAX_HISTORY) undoStack.removeLast();
        redoStack.clear();      // a new change invalidates the redo history
        lastSnapshot = now;
    }

    private void onPeriodChanged() {
        boolean wasPaused = historyPaused;
        historyPaused = true;   // day clamping shouldn't create its own history entries
        try {
            updateDayOptions();
        } finally {
            historyPaused = wasPaused;
        }
        markDirty();
        sTable.refresh();
        pTable.refresh();
        recordHistory();        // year/month change + any clamped days = one undo step
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        sTable.edit(-1, null);  // cancel any in-progress cell edit
        pTable.edit(-1, null);
        redoStack.push(lastSnapshot);
        restoreSnapshot(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        sTable.edit(-1, null);
        pTable.edit(-1, null);
        undoStack.push(lastSnapshot);
        restoreSnapshot(redoStack.pop());
    }

    private void restoreSnapshot(Snapshot s) {
        historyPaused = true;
        try {
            if (s.year() != null) years.setValue(s.year());
            if (s.monthIndex() >= 0) months.getSelectionModel().select(s.monthIndex());
            updateDayOptions();

            List<SaleRow> sales = new java.util.ArrayList<>();
            for (SaleState st : s.sales()) {
                SaleRow r = new SaleRow();
                r.dayProperty().set(st.day());
                r.invoiceNoProperty().set(st.invoice());
                r.exemptProperty().set(st.exempt());
                r.zeroRatedProperty().set(st.zeroRated());
                r.grossInputProperty().set(st.gross());   // last: recalculates taxable
                sales.add(r);
            }
            List<PurchaseRow> purchases = new java.util.ArrayList<>();
            for (PurchaseState st : s.purchases()) {
                PurchaseRow r = new PurchaseRow();
                r.dayProperty().set(st.day());
                r.supplierProperty().set(st.supplier());
                r.invoiceNoProperty().set(st.invoice());
                r.exemptProperty().set(st.exempt());
                r.zeroRatedProperty().set(st.zeroRated());
                r.grossInputProperty().set(st.gross());   // last: recalculates taxable
                purchases.add(r);
            }
            saleRows.setAll(sales);
            purchaseRows.setAll(purchases);

            sTable.getSelectionModel().clearSelection();
            pTable.getSelectionModel().clearSelection();
            sTable.refresh();
            pTable.refresh();
        } finally {
            historyPaused = false;
        }
        lastSnapshot = s;
    }

    // ---------- Unsaved-changes tracking ----------

    private void markDirty() {
        if (loading || dirty) return;
        dirty = true;
        tradeNameText.setText(baseTitle + "*");
    }

    private void markClean() {
        dirty = false;
        tradeNameText.setText(baseTitle);
    }

    private boolean confirmLeave() {
        if (!dirty) return true;

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Don't Save", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, null, save, discard, cancel);
        alert.setTitle("Unsaved changes");
        alert.setHeaderText("This SLSP has unsaved changes.");
        alert.setContentText("Do you want to save before continuing?");
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/app/views/fxml/images/logo.png")));
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == cancel) return false;

        if (result.get() == save) {
            if (!persistSlsp()) return false;
            markClean();
        }
        return true;
    }

    // ---------- Persistence ----------

    private String buildPeriod() {
        int year = years.getValue();
        int monthIndex = months.getSelectionModel().getSelectedIndex();
        return String.format("%04d-%02d", year, monthIndex + 1);
    }

    private boolean persistSlsp() {
        if (years.getValue() == null || months.getSelectionModel().getSelectedIndex() < 0) {
            showAlert("Cannot save", "Please select both a month and a year.");
            return false;
        }
        try {
            String period = buildPeriod();
            YearMonth ym = YearMonth.parse(period);

            // Stored values are unchanged: exempt, zero-rated and taxable (taxable is computed from the gross input)
            List<Sale> sales = new java.util.ArrayList<>();
            for (SaleRow row : saleRows) {
                if (row.isEmpty()) continue;
                BigDecimal gross = row.grossProperty().get();
                if (gross == null || gross.signum() == 0) {
                    showAlert("Cannot save", "Sale row " + (saleRows.indexOf(row) + 1)
                            + " has a Total Amount of zero. Enter an amount or delete the row.");
                    return false;
                }
                Sale sale = new Sale();
                sale.setSaleDate(ym.atDay(row.dayProperty().get()).toString());
                sale.setSiNum(row.invoiceNoProperty().get());
                sale.setExemptAmount(row.exemptProperty().get());
                sale.setZeroRatedAmount(row.zeroRatedProperty().get());
                sale.setGrossAmount(gross);
                sales.add(sale);
            }

            List<Purchase> purchases = new java.util.ArrayList<>();
            for (PurchaseRow row : purchaseRows) {
                if (row.isEmpty()) continue;
                BigDecimal gross = row.grossProperty().get();
                if (gross == null || gross.signum() == 0) {
                    showAlert("Cannot save", "Purchase row " + (purchaseRows.indexOf(row) + 1)
                            + " has a Gross Taxable amount of zero. Enter an amount or delete the row.");
                    return false;
                }
                Purchase purchase = new Purchase();
                purchase.setPurchaseDate(ym.atDay(row.dayProperty().get()).toString());
                Supplier s = row.supplierProperty().get();
                if (s == null) {
                    showAlert("Cannot save", "Every purchase row needs a supplier selected.");
                    return false;
                }
                purchase.setSupplierId(s.getId());
                purchase.setInvoiceNum(row.invoiceNoProperty().get());
                purchase.setExemptAmount(row.exemptProperty().get());
                purchase.setZeroRatedAmount(row.zeroRatedProperty().get());
                purchase.setGrossAmount(gross);
                purchases.add(purchase);
            }

            // Returns false when another SLSP already owns this period,
            // both when creating a new SLSP and when editing one and moving it to a taken month/year
            boolean saved = vm.save(period, sales, purchases);
            if (!saved) {
                showAlert("Cannot save", "An SLSP for this month and year already exists.");
                return false;
            }

            delBtn.setVisible(true);
            SlspController.lastSelectedPeriod = period;
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert("Save failed", "An error occurred while saving: " + ex.getMessage());
            return false;
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/app/views/fxml/images/logo.png")));
        alert.showAndWait();
    }

    // ---------- sum calculator (unchanged) ----------


    @FXML
    public void sumInput(ActionEvent e) {
        String input = sumInput.getText();

        if (input == null || input.isBlank()) {
            return;
        }

        BigDecimal inputAmount = parsePeso(input);
        total.add(inputAmount);

        BigDecimal sum = BigDecimal.ZERO;
        sumContainer.getChildren().clear();
        for (BigDecimal amount : total) {
            TextField newTextField = new TextField(
                    "₱ " + String.format("%,.2f", amount)
            );

            newTextField.setEditable(false);
            newTextField.setStyle("""
                    -fx-background-color: transparent;
                    -fx-background-insets: 0;
                    -fx-background-radius: 0;
                    -fx-padding: 0;
                    -fx-text-fill: black;
                    """);
            newTextField.setAlignment(javafx.geometry.Pos.CENTER_RIGHT); 
            newTextField.addEventHandler(
                    MouseEvent.MOUSE_CLICKED,
                    event ->{
                    	if (event.getButton() == MouseButton.SECONDARY) {
                    }
                        deleteTextField(amount, newTextField, e);
                    }
            );

            sum = sum.add(amount);
            sumContainer.getChildren().add(newTextField);
        }

        totalSum.setText("Total: ₱ " + String.format("%,.2f", sum));
        sumInput.setText("");
    }

    private void deleteTextField(BigDecimal d, TextField t, ActionEvent e) {
        if (total.size() <= 0) return;
        sumContainer.getChildren().remove(t);
        total.remove(d);
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal amount : total) sum = sum.add(amount);
        totalSum.setText("Total: " + sum);
    }

    @FXML
    public void clearSumInput(ActionEvent e) {
        sumInput.setText("");
        total.clear();
        sumContainer.getChildren().clear();
        totalSum.setText("Total: 0");
    }

    // ---------- navigation ----------

    @FXML
    public void back(ActionEvent e) {
        if (!confirmLeave()) return;
        editingSlsp = null;
        Pages.change(e, Pages.SLSP);
    }

    @FXML
    public void SaveSlsp(ActionEvent e) {
        if (!persistSlsp()) return;

        sortRows();
        markClean();

        editingSlsp = vm.getSlsp();
    }

    private void sortRows() {
        // Finish editing before sorting.
        sTable.edit(-1, null);
        pTable.edit(-1, null);

        historyPaused = true;

        try {
            FXCollections.sort(saleRows, SALE_ORDER);
            FXCollections.sort(purchaseRows, purchaseOrder);
        } finally {
            historyPaused = false;
        }

        // Update the undo baseline after sorting.
        lastSnapshot = captureSnapshot();

        // Refresh row numbers and displayed values.
        sTable.refresh();
        pTable.refresh();
    }

    @FXML
    public void DeleteSlsp(ActionEvent e) {
        if (!vm.isExisting()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete this SLSP and all its sales/purchases? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete SLSP");
        confirm.setHeaderText(null);
        Stage stage = (Stage) confirm.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/app/views/fxml/images/logo.png")));
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) return;

        try {
            vm.deleteCurrentSlsp();
            dirty = false;
            editingSlsp = null;
            Pages.change(e, Pages.SLSP);
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert("Delete failed", "An error occurred while deleting: " + ex.getMessage());
        }
    }


    @FXML
    public void Supplier(ActionEvent e) {
        if (!confirmLeave()) return;
        // If this SLSP exists in the DB (already existing, or just saved via the prompt),
        // make sure coming back reloads it in edit state instead of as a new one.
        if (vm.isExisting()) {
            editingSlsp = vm.getSlsp();
        }
        Pages.change(e, Pages.SUPPLIER);
    }
    private static class MoneyCell<T> extends TextFieldTableCell<T, BigDecimal> {
        MoneyCell() {
            super(MONEY_CONVERTER);
            setAlignment(Pos.CENTER_RIGHT);
        }
        @Override public void startEdit() {
            super.startEdit();
            if (isEditing() && getGraphic() instanceof TextField tf) {
                tf.setText(nz(getItem()).setScale(2, RoundingMode.HALF_UP).toPlainString());
                tf.selectAll();
            }
        }
    }
    private static class MoneyDisplayCell<T> extends TableCell<T, BigDecimal> {
        MoneyDisplayCell() {
            setAlignment(Pos.CENTER_RIGHT);
            // a selected row keeps the normal selection colors, so re-apply the style when selection changes
            selectedProperty().addListener(_ -> applyCalcStyle());
        }

        @Override protected void updateItem(BigDecimal v, boolean empty) {
            super.updateItem(v, empty);
            setText(empty || v == null ? null : displayMoney(v));
            applyCalcStyle();
        }

        private void applyCalcStyle() {
            setStyle(isEmpty() || isSelected() ? "" : CALC_CELL_STYLE);
        }
    }
    private void styleCalculatedFooter(TextField... fields) {
        for (TextField f : fields) f.setStyle(CALC_FOOTER_STYLE);
    }
    private <T> void wireMoneyColumn(TableColumn<T, BigDecimal> col, Function<T, Property<BigDecimal>> extractor) {
        col.setEditable(true);
        col.setCellValueFactory(cd -> extractor.apply(cd.getValue()));
        col.setCellFactory(_ -> new MoneyCell<>());
        col.setOnEditCommit(ev -> extractor.apply(ev.getRowValue()).setValue(ev.getNewValue()));
    }

	private static Observable[] saleDeps(SaleRow r) {
		return new Observable[] { r.exemptProperty(), r.zeroRatedProperty(), r.grossProperty(), r.taxableProperty() };
	}

	private static Observable[] purchaseDeps(PurchaseRow r) {
		return new Observable[] { r.exemptProperty(), r.zeroRatedProperty(), r.grossProperty(), r.taxableProperty() };
	}
	private void alignFooter(GridPane footer, TableView<?> table) {
	    footer.setHgap(0);
	    var constraints = footer.getColumnConstraints();
	    for (int i = 0; i < constraints.size() && i < table.getColumns().size(); i++) {
	        TableColumn<?, ?> col = table.getColumns().get(i);
	        ColumnConstraints cc = constraints.get(i);
	        var width = Bindings.when(col.visibleProperty()).then(col.widthProperty()).otherwise(0);
	        cc.minWidthProperty().bind(width);
	        cc.prefWidthProperty().bind(width);
	        cc.maxWidthProperty().bind(width);
	    }
	    for (Node n : footer.getChildren()) {
	        if (n instanceof TextField) GridPane.setMargin(n, new Insets(0, 3, 0, 3));
	    }
	}
	private void formatPesoField(TextField field) {
	    field.setOnAction(_ -> formatPesoFieldDisplay(field));

	    field.focusedProperty().addListener((_, _, isFocused) -> {
	        if (isFocused) {
	            field.setText(field.getText()
	                    .replace("₱", "")
	                    .replace(",", "")
	                    .trim());
	        } else {
	            formatPesoFieldDisplay(field);
	        }
	    });

	    formatPesoFieldDisplay(field);
	}

	private void formatPesoFieldDisplay(TextField field) {
	    try {
	        String text = field.getText()
	                .replace("₱", "")
	                .replace(",", "")
	                .trim();

	        BigDecimal amount = new BigDecimal(text.isEmpty() ? "0" : text);
	        field.setText("₱ " + String.format("%,.2f", amount));
	    } catch (NumberFormatException ex) {
	        field.setText("₱ 0.00");
	    }
	}

	private BigDecimal parsePeso(String text) {
	    return new BigDecimal(
	            text.replace("₱", "").replace(",", "").trim()
	    );
	}
	private StringConverter<Supplier> supplierConverter() {
	    return new StringConverter<>() {
	        @Override public String toString(Supplier s) {
	            if (s == null) return "";
	            String name = s.getTradeName() == null ? "" : s.getTradeName();
	            String tin = s.getTinNum() == null ? "" : s.getTinNum();
	            return name + ": " + tin;
	        }
	        @Override public Supplier fromString(String text) {
	            // only used if the combo is ever made editable: match back to a real supplier
	            return suppliers.stream()
	                    .filter(s -> toString(s).equals(text))
	                    .findFirst().orElse(null);
	        }
	    };
	}
}