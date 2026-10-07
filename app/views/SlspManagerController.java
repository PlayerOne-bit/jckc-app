package app.views;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.StringConverter;

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
    @FXML private Button sAddBtn;
    @FXML private Text saleTotalRows;
    @FXML private TextField saleExempt;
    @FXML private TextField saleZeroRated;
    @FXML private TextField saleTaxable;
    @FXML private TextField outputVat;
    @FXML private TextField saleGross;

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
    @FXML private Button pAddBtn;
    @FXML private Text purchaseTotalRows;
    @FXML private TextField purchaseExempt;
    @FXML private TextField purchaseZeroRated;
    @FXML private TextField purchaseTaxable;
    @FXML private TextField inputVat;
    @FXML private TextField purchaseGross;

    private final ObservableList<SaleRow> saleRows = FXCollections.observableArrayList(
            r -> new Observable[] { r.dayProperty(), r.invoiceNoProperty(),
                    r.exemptProperty(), r.zeroRatedProperty(), r.taxableProperty() });

    private final ObservableList<PurchaseRow> purchaseRows = FXCollections.observableArrayList(
            r -> new Observable[] { r.dayProperty(), r.supplierProperty(), r.invoiceNoProperty(),
                    r.exemptProperty(), r.zeroRatedProperty(), r.taxableProperty() });

    // Used once when loading an existing SLSP (no live sorting, so new rows stay at the bottom)
    private static final Comparator<SaleRow> SALE_ORDER = Comparator
            .comparingInt((SaleRow r) -> r.dayProperty().get())
            .thenComparing(r -> r.invoiceNoProperty().get(), Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));

    private static final Comparator<PurchaseRow> PURCHASE_ORDER = Comparator
            .comparingInt((PurchaseRow r) -> r.dayProperty().get())
            .thenComparing(r -> r.invoiceNoProperty().get(), Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));

    private final ObservableList<Integer> dayOptions = FXCollections.observableArrayList();
    private ObservableList<Supplier> suppliers = FXCollections.observableArrayList();

    private List<BigDecimal> total = new java.util.ArrayList<>();

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");

    private SlspManagerViewModel vm;
    private boolean dirty = false;
    private boolean loading = false;
    private String baseTitle;

    @FXML
    private void initialize() throws Exception {
        Pages.bindShortcut(root, "Ctrl+S", this::SaveSlsp);
        Pages.bindShortcut(root, "Ctrl+D", this::DeleteSlsp);
        Pages.bindShortcut(root, "ESC", this::back);
        Pages.bindShortcut(sumInput, "Enter", this::sumInput);
        Pages.bindShortcut(sumInput, "Ctrl+Delete", this::clearSumInput);
        Pages.bindShortcut(root, "Ctrl+E", sumInput);
        Pages.bindShortcut(root, "Ctrl+B", this::Supplier);
        Pages.bindShortcut(root, "Alt+N", this::onAddRowShortcut);

        baseTitle = "SLSP : " + Taxpayer.getTaxpayer().getTradeName();
        tradeNameText.setText(baseTitle);

        vm = new SlspManagerViewModel();

        years.setItems(FXCollections.observableArrayList(LocalDate.now().getYear(), LocalDate.now().getYear() - 1));
        months.setItems(FXCollections.observableArrayList(
                "01 - January", "02 - February", "03 - March", "04 - April",
                "05 - May", "06 - June", "07 - July", "08 - August",
                "09 - September", "10 - October", "11 - November", "12 - December"));

        suppliers.setAll(new SupplierViewModel().loadSuppliers());

        setUpNumberTextField(sumInput);
        setupSaleTable();
        setupPurchaseTable();

        sAddBtn.setOnAction(_ -> addPurchaseOrSaleRow(true));
        pAddBtn.setOnAction(_ -> addPurchaseOrSaleRow(false));

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

        saleRows.addListener((javafx.collections.ListChangeListener<SaleRow>) _ -> {
            markDirty();
            updateSaleTotals();
        });
        purchaseRows.addListener((javafx.collections.ListChangeListener<PurchaseRow>) _ -> {
            markDirty();
            updatePurchaseTotals();
        });
        years.valueProperty().addListener((_, _, _) -> { updateDayOptions(); markDirty(); sTable.refresh(); pTable.refresh(); });
        months.valueProperty().addListener((_, _, _) -> { updateDayOptions(); markDirty(); sTable.refresh(); pTable.refresh(); });
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

        for (Sale sale : vm.getSales()) {
            SaleRow row = new SaleRow();
            row.dayProperty().set(LocalDate.parse(sale.getSaleDate()).getDayOfMonth());
            row.invoiceNoProperty().set(sale.getSiNum());
            row.exemptProperty().set(sale.getExemptAmount());
            row.zeroRatedProperty().set(sale.getZeroRatedAmount());
            row.taxableProperty().set(sale.getTaxableAmount());
            saleRows.add(row);
        }

        for (Purchase purchase : vm.getPurchases()) {
            PurchaseRow row = new PurchaseRow();
            row.dayProperty().set(LocalDate.parse(purchase.getPurchaseDate()).getDayOfMonth());
            Supplier matchingSupplier = suppliers.stream()
                    .filter(s -> s.getId() == purchase.getSupplierId())
                    .findFirst().orElse(null);
            row.supplierProperty().set(matchingSupplier);
            row.invoiceNoProperty().set(purchase.getInvoiceNum());
            row.exemptProperty().set(purchase.getExemptAmount());
            row.zeroRatedProperty().set(purchase.getZeroRatedAmount());
            row.taxableProperty().set(purchase.getTaxableAmount());
            purchaseRows.add(row);
        }

        // Sort once on load; after this, rows stay in insertion order
        FXCollections.sort(saleRows, SALE_ORDER);
        FXCollections.sort(purchaseRows, PURCHASE_ORDER);
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
            gross = gross.add(r.getGross());
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
                taxable = BigDecimal.ZERO, vat = BigDecimal.ZERO, gross = BigDecimal.ZERO;
        int count = 0;
        for (PurchaseRow r : purchaseRows) {
            if (r.isEmpty()) continue;
            count++;
            exempt = exempt.add(nz(r.exemptProperty().get()));
            zeroRated = zeroRated.add(nz(r.zeroRatedProperty().get()));
            taxable = taxable.add(nz(r.taxableProperty().get()));
            vat = vat.add(r.getInputVat());
            gross = gross.add(r.getGross());
        }
        if (purchaseTotalRows != null) purchaseTotalRows.setText("Total for " + count + "purchase row" + (count == 1 ? ":" : "s:"));
        if (purchaseExempt != null) purchaseExempt.setText(formatMoney(exempt));
        if (purchaseZeroRated != null) purchaseZeroRated.setText(formatMoney(zeroRated));
        if (purchaseTaxable != null) purchaseTaxable.setText(formatMoney(taxable));
        if (inputVat != null) inputVat.setText(formatMoney(vat));
        if (purchaseGross != null) purchaseGross.setText(formatMoney(gross));
    }

    // ---------- Even, locked column widths ----------

    private void setupEvenColumns(TableView<?> table, int columnCount) {
        for (TableColumn<?, ?> col : table.getColumns()) {
            col.prefWidthProperty().bind(table.widthProperty().subtract(2).divide(columnCount));
        }
    }

    // ---------- Column wiring helpers ----------

    private static final StringConverter<BigDecimal> MONEY_CONVERTER = new StringConverter<>() {
        @Override public String toString(BigDecimal v) {
            return v == null ? "0.00" : v.setScale(2, java.math.RoundingMode.HALF_UP).toString();
        }
        @Override public BigDecimal fromString(String s) {
            try {
                if (s == null || s.isBlank()) return BigDecimal.ZERO;
                return new BigDecimal(s).setScale(2, java.math.RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }
    };

    private <T> void wireMoneyColumn(TableColumn<T, BigDecimal> col, Function<T, Property<BigDecimal>> extractor) {
        col.setEditable(true);
        col.setCellValueFactory(cd -> extractor.apply(cd.getValue()));
        col.setCellFactory(TextFieldTableCell.forTableColumn(MONEY_CONVERTER));
        col.setOnEditCommit(ev -> extractor.apply(ev.getRowValue()).setValue(ev.getNewValue()));
    }

    private <T> void wireComputedColumn(TableColumn<T, BigDecimal> col, Function<T, BigDecimal> compute) {
        col.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(compute.apply(cd.getValue())));
        col.setCellFactory(_ -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP).toString());
            }
        });
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

    private <T> void wireDayColumn(TableColumn<T, Integer> col, Function<T, IntegerProperty> extractor) {
        col.setEditable(true);
        col.setCellValueFactory(cd -> extractor.apply(cd.getValue()).asObject());
        col.setCellFactory(ComboBoxTableCell.forTableColumn(buildDayConverter(), dayOptions));
        col.setOnEditCommit(ev -> extractor.apply(ev.getRowValue()).set(ev.getNewValue()));
    }

    // ---------- Sale table setup ----------

    private void setupSaleTable() {
        sTable.setItems(saleRows);
        setupEvenColumns(sTable, 8);

        wireRowNumberColumn(sColNum);
        wireDayColumn(sColDate, SaleRow::dayProperty);

        sColInvoice.setEditable(true);
        sColInvoice.setCellValueFactory(cd -> cd.getValue().invoiceNoProperty());
        sColInvoice.setCellFactory(TextFieldTableCell.forTableColumn());
        sColInvoice.setOnEditCommit(ev -> ev.getRowValue().invoiceNoProperty().set(ev.getNewValue()));

        wireMoneyColumn(sColExempt, SaleRow::exemptProperty);
        wireMoneyColumn(sColZeroRated, SaleRow::zeroRatedProperty);
        wireMoneyColumn(sColTaxable, SaleRow::taxableProperty);
        wireComputedColumn(sColOutputVat, SaleRow::getOutputVat);
        wireComputedColumn(sColGross, SaleRow::getGross);

        setupCellNavigation(sTable,
                List.<TableColumn<SaleRow, ?>>of(sColDate, sColInvoice, sColExempt, sColZeroRated, sColTaxable),
                () -> addPurchaseOrSaleRow(true));
    }

    // ---------- Purchase table setup ----------

    private void setupPurchaseTable() {
        pTable.setItems(purchaseRows);
        setupEvenColumns(pTable, 9);

        wireRowNumberColumn(pColNum);
        wireDayColumn(pColDate, PurchaseRow::dayProperty);

        pColSupplier.setEditable(true);
        pColSupplier.setCellValueFactory(cd -> cd.getValue().supplierProperty());
        pColSupplier.setCellFactory(ComboBoxTableCell.forTableColumn(new StringConverter<Supplier>() {
            @Override public String toString(Supplier s) {
                return s == null ? "" : s.getTinNum() + " - " + s.getTradeName();
            }
            @Override public Supplier fromString(String s) { return null; }
        }, suppliers));
        pColSupplier.setOnEditCommit(ev -> ev.getRowValue().supplierProperty().set(ev.getNewValue()));

        pColInvoice.setEditable(true);
        pColInvoice.setCellValueFactory(cd -> cd.getValue().invoiceNoProperty());
        pColInvoice.setCellFactory(TextFieldTableCell.forTableColumn());
        pColInvoice.setOnEditCommit(ev -> ev.getRowValue().invoiceNoProperty().set(ev.getNewValue()));

        wireMoneyColumn(pColExempt, PurchaseRow::exemptProperty);
        wireMoneyColumn(pColZeroRated, PurchaseRow::zeroRatedProperty);
        wireMoneyColumn(pColTaxable, PurchaseRow::taxableProperty);
        wireComputedColumn(pColInputVat, PurchaseRow::getInputVat);
        wireComputedColumn(pColGross, PurchaseRow::getGross);

        setupCellNavigation(pTable,
                List.<TableColumn<PurchaseRow, ?>>of(pColDate, pColSupplier, pColInvoice, pColExempt, pColZeroRated, pColTaxable),
                () -> addPurchaseOrSaleRow(false));
    }

    // ---------- Keyboard navigation: Enter = new bottom row, Tab / Shift+Tab = next / previous cell ----------

    private <T> void setupCellNavigation(TableView<T> table,
                                         List<TableColumn<T, ?>> editableCols,
                                         Runnable addRow) {
        table.setEditable(true);

        // Event filter runs before the cell's TextField/ComboBox sees the key
        table.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            KeyCode code = ev.getCode();

            // DOWN on the last row adds a row (only when not editing)
            if (code == KeyCode.DOWN && table.getEditingCell() == null) {
                if (table.getSelectionModel().getSelectedIndex() == table.getItems().size() - 1) {
                    addRow.run();
                    ev.consume();
                }
                return;
            }

            if (code != KeyCode.TAB && code != KeyCode.ENTER) return;
            if (ev.isControlDown() || ev.isAltDown()) return;

            // Where are we now?
            TablePosition<T, ?> editing = table.getEditingCell();
            int row = editing != null ? editing.getRow()
                                      : table.getSelectionModel().getSelectedIndex();
            int colIdx = editing != null ? editableCols.indexOf(editing.getTableColumn()) : -1;

            // Commit whatever is being typed (fires the cell's onAction -> commitEdit)
            if (ev.getTarget() instanceof TextField tf) {
                tf.fireEvent(new ActionEvent(tf, tf));
            }

            int n = editableCols.size();
            int targetRow = row;
            int targetCol;

            if (code == KeyCode.ENTER) {
                // New row at the bottom, start editing its first column
                addRow.run();
                targetRow = table.getSelectionModel().getSelectedIndex();
                targetCol = 0;
            } else {
                int step = ev.isShiftDown() ? -1 : 1;
                targetCol = colIdx < 0 ? (step > 0 ? 0 : n - 1) : colIdx + step;

                if (targetCol >= n) {            // past last column -> next row
                    targetCol = 0;
                    targetRow++;
                } else if (targetCol < 0) {      // before first column -> previous row
                    targetCol = n - 1;
                    targetRow--;
                }

                if (targetRow < 0) { targetRow = 0; targetCol = 0; }

                if (targetRow >= table.getItems().size()) {   // Tab off the end -> new row
                    addRow.run();
                    targetRow = table.getSelectionModel().getSelectedIndex();
                    targetCol = 0;
                }
            }

            final int r = Math.max(targetRow, 0);
            final int c = targetCol;
            ev.consume();

            // Wait for the commit to finish before starting the next edit
            Platform.runLater(() -> {
                table.getSelectionModel().clearAndSelect(r);
                table.scrollTo(r);
                table.edit(r, editableCols.get(c));
            });
        });
    }

    // ---------- Row creation: guarded, no duplicate blank rows, always at the bottom ----------

    private void onAddRowShortcut(ActionEvent e) {
        boolean isSaleTab = tabPane.getSelectionModel().getSelectedIndex() == 0;
        addPurchaseOrSaleRow(isSaleTab);
    }

    private void addPurchaseOrSaleRow(boolean isSale) {
        if (isSale) {
            SaleRow existingBlank = saleRows.stream().filter(SaleRow::isEmpty).findFirst().orElse(null);
            if (existingBlank != null) {
                selectSaleRow(existingBlank);
                return;
            }
            SaleRow row = new SaleRow();
            saleRows.add(row);
            selectSaleRow(row);
        } else {
            PurchaseRow existingBlank = purchaseRows.stream().filter(PurchaseRow::isEmpty).findFirst().orElse(null);
            if (existingBlank != null) {
                selectPurchaseRow(existingBlank);
                return;
            }
            PurchaseRow row = new PurchaseRow();
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

            List<Sale> sales = new java.util.ArrayList<>();
            for (SaleRow row : saleRows) {
                if (row.isEmpty()) continue;
                Sale sale = new Sale();
                sale.setSaleDate(ym.atDay(row.dayProperty().get()).toString());
                sale.setSiNum(row.invoiceNoProperty().get());
                sale.setExemptAmount(row.exemptProperty().get());
                sale.setZeroRatedAmount(row.zeroRatedProperty().get());
                sale.setTaxableAmount(row.taxableProperty().get());
                sales.add(sale);
            }

            List<Purchase> purchases = new java.util.ArrayList<>();
            for (PurchaseRow row : purchaseRows) {
                if (row.isEmpty()) continue;
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
                purchase.setTaxableAmount(row.taxableProperty().get());
                purchases.add(purchase);
            }

            boolean saved = vm.save(period, sales, purchases);
            if (!saved) {
                showAlert("Cannot save", "An SLSP for this month and year already exists.");
                return false;
            }

            delBtn.setVisible(true);
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
        alert.showAndWait();
    }

    // ---------- sum calculator (unchanged) ----------

    private void setUpNumberTextField(TextField t) {
        t.textProperty().addListener((_, old, newVal) -> {
            if (!newVal.matches("^-?\\d*(\\.\\d{0,2})?$")) t.setText(old);
        });
    }

    @FXML
    public void sumInput(ActionEvent e) {
        String input = sumInput.getText();
        if (input == null || input.isBlank()) return;
        BigDecimal inputAmount = new BigDecimal(input);
        total.add(inputAmount);
        BigDecimal sum = BigDecimal.ZERO;
        sumContainer.getChildren().clear();
        for (BigDecimal amount : total) {
            TextField newTextField = new TextField("" + amount);
            newTextField.setEditable(false);
            newTextField.setStyle("""
                    -fx-background-color: none;
                    -fx-background-insets: 0;
                    -fx-background-radius: 0;
                    -fx-padding: 0;
                    -fx-text-fill:black;
                    -fx-alignment:center;
                    """);
            newTextField.addEventHandler(MouseEvent.MOUSE_CLICKED, _ -> deleteTextField(amount, newTextField, e));
            sum = sum.add(amount);
            sumContainer.getChildren().add(newTextField);
        }
        totalSum.setText("Total: " + sum);
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
        if (persistSlsp()) {
            markClean();
            editingSlsp = null;
            Pages.change(e, Pages.SLSP);
        }
    }

    @FXML
    public void DeleteSlsp(ActionEvent e) {
        if (!vm.isExisting()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete this SLSP and all its sales/purchases? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete SLSP");
        confirm.setHeaderText(null);

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
    public void PrintSlsp(ActionEvent e) {
        if (!confirmLeave()) return;
        // TODO: print
    }

    @FXML
    public void Supplier(ActionEvent e) {
        if (!confirmLeave()) return;
        Pages.change(e, Pages.SUPPLIER);
    }
}