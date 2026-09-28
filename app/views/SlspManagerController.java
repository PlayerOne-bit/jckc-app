package app.views;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import app.models.Supplier;
import app.models.Taxpayer;
import app.viewmodels.SlspViewModel;
import app.viewmodels.SupplierViewModel;
import javafx.beans.Observable;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.StringConverter;

public class SlspManagerController {
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

    private final ObservableList<SaleRow> saleRows = FXCollections.observableArrayList(
            r -> new Observable[] { r.dayProperty(), r.invoiceNoProperty(),
                    r.exemptProperty(), r.zeroRatedProperty(), r.taxableProperty() });

    private final ObservableList<PurchaseRow> purchaseRows = FXCollections.observableArrayList(
            r -> new Observable[] { r.dayProperty(), r.supplierProperty(), r.invoiceNoProperty(),
                    r.exemptProperty(), r.zeroRatedProperty(), r.taxableProperty() });

    private boolean dirty = false;
    private String baseTitle;
    private final ObservableList<Integer> dayOptions = FXCollections.observableArrayList();
    private ObservableList<Supplier> suppliers = FXCollections.observableArrayList();

    private List<BigDecimal> total = new java.util.ArrayList<>();

    @FXML
    private void initialize() throws Exception {
        Pages.bindShortcut(root, "Ctrl+S", this::SaveSlsp);
        Pages.bindShortcut(root, "Ctrl+D", this::DeleteSlsp);
        Pages.bindShortcut(root, "ESC", this::back);
        Pages.bindShortcut(sumInput, "Enter", this::sumInput);
        Pages.bindShortcut(sumInput, "Ctrl+Delete", this::clearSumInput);
        Pages.bindShortcut(root, "Ctrl+E", sumInput);
        Pages.bindShortcut(root, "Ctrl+B", this::Supplier);

        baseTitle = "SLSP : " + Taxpayer.getTaxpayer().getTradeName();
        tradeNameText.setText(baseTitle);
        years.setValue(LocalDate.now().getYear());
        years.setItems(FXCollections.observableArrayList(LocalDate.now().getYear(), LocalDate.now().getYear() - 1));
        months.setItems(FXCollections.observableArrayList(
                "01 - January", "02 - February", "03 - March", "04 - April",
                "05 - May", "06 - June", "07 - July", "08 - August",
                "09 - September", "10 - October", "11 - November", "12 - December"));
        months.getSelectionModel().selectFirst();

        years.valueProperty().addListener((_, _, _) -> updateDayOptions());
        months.valueProperty().addListener((_, _, _) -> updateDayOptions());
        updateDayOptions();

        suppliers.setAll(new SupplierViewModel().loadSuppliers());

        delBtn.setVisible(!SlspViewModel.isNew());
        setUpNumberTextField(sumInput);

        setupSaleTable();
        setupPurchaseTable();

        sAddBtn.setOnAction(_ -> addPurchaseOrSaleRow(true));
        pAddBtn.setOnAction(_ -> addPurchaseOrSaleRow(false));
    }

    // ---------- Day-of-month restriction ----------

    private void updateDayOptions() {
        int year = years.getValue() != null ? years.getValue() : LocalDate.now().getYear();
        int monthIndex = months.getSelectionModel().getSelectedIndex();
        int month = monthIndex >= 0 ? monthIndex + 1 : LocalDate.now().getMonthValue();
        int max = YearMonth.of(year, month).lengthOfMonth();

        List<Integer> days = new java.util.ArrayList<>();
        for (int d = 1; d <= max; d++) days.add(d);
        dayOptions.setAll(days);

        // clamp any already-entered days that no longer fit (e.g. switching from a 31-day to Feb)
        for (SaleRow r : saleRows) if (r.dayProperty().get() > max) r.dayProperty().set(max);
        for (PurchaseRow r : purchaseRows) if (r.dayProperty().get() > max) r.dayProperty().set(max);
    }

    // ---------- Even, locked column widths ----------

    private void setupEvenColumns(TableView<?> table, int columnCount) {
        for (TableColumn<?, ?> col : table.getColumns()) {
            col.prefWidthProperty().bind(table.widthProperty().subtract(2).divide(columnCount));
        }
    }

    // ---------- Numeric editable column helper ----------

    private static final StringConverter<BigDecimal> MONEY_CONVERTER = new StringConverter<>() {
        @Override public String toString(BigDecimal v) {
            return v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_UP).toString();
        }
        @Override public BigDecimal fromString(String s) {
            try {
                if (s == null || s.isBlank()) return BigDecimal.ZERO;
                return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
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
                setText(empty || v == null ? null : v.setScale(2, RoundingMode.HALF_UP).toString());
            }
        });
    }

    private <T> void wireRowNumberColumn(TableColumn<T, Integer> col) {
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
        col.setCellFactory(ComboBoxTableCell.forTableColumn(dayOptions));
        col.setOnEditCommit(ev -> extractor.apply(ev.getRowValue()).set(ev.getNewValue()));
    }

    // ---------- Sale table setup ----------

    private void setupSaleTable() {
        sTable.setItems(saleRows);
        setupEvenColumns(sTable, 8);

        wireRowNumberColumn(sColNum);
        wireDayColumn(sColDate, SaleRow::dayProperty);//The type SaleRow does not define dayProperty(T) that is applicable here

        sColInvoice.setEditable(true);
        sColInvoice.setCellValueFactory(cd -> cd.getValue().invoiceNoProperty());
        sColInvoice.setCellFactory(TextFieldTableCell.forTableColumn());
        sColInvoice.setOnEditCommit(ev -> ev.getRowValue().invoiceNoProperty().set(ev.getNewValue()));

        wireMoneyColumn(sColExempt, SaleRow::exemptProperty);
        wireMoneyColumn(sColZeroRated, SaleRow::zeroRatedProperty);
        wireMoneyColumn(sColTaxable, SaleRow::taxableProperty);
        wireComputedColumn(sColOutputVat, SaleRow::getOutputVat);
        wireComputedColumn(sColGross, SaleRow::getGross);

        setupRowNavigation(sTable, saleRows, () -> addPurchaseOrSaleRow(true));
    }

    // ---------- Purchase table setup ----------

    private void setupPurchaseTable() {
        pTable.setItems(purchaseRows);
        setupEvenColumns(pTable, 9);

        wireRowNumberColumn(pColNum);
        wireDayColumn(pColDate, PurchaseRow::dayProperty);//he type PurchaseRow does not define dayProperty(T) that is applicable here

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

        setupRowNavigation(pTable, purchaseRows, () -> addPurchaseOrSaleRow(false));
    }

    // ---------- Row creation: guarded, no duplicate blank rows ----------

    private <T> void setupRowNavigation(TableView<T> table, ObservableList<T> rows, Runnable addRow) {
        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.DOWN) {
                int selected = table.getSelectionModel().getSelectedIndex();
                if (selected == rows.size() - 1) {
                    addRow.run();
                    ev.consume();
                }
            }
        });
    }

    private void onAddRowShortcut(ActionEvent e) {
        boolean isSaleTab = tabPane.getSelectionModel().getSelectedIndex() == 0;
        addPurchaseOrSaleRow(isSaleTab);
    }

    private void addPurchaseOrSaleRow(boolean isSale) {
        if (isSale) {
            if (!saleRows.isEmpty() && saleRows.get(saleRows.size() - 1).isEmpty()) {
                sTable.getSelectionModel().select(saleRows.size() - 1);
                return;
            }
            saleRows.add(new SaleRow());
            sTable.getSelectionModel().select(saleRows.size() - 1);
            sTable.scrollTo(saleRows.size() - 1);
        } else {
            if (!purchaseRows.isEmpty() && purchaseRows.get(purchaseRows.size() - 1).isEmpty()) {
                pTable.getSelectionModel().select(purchaseRows.size() - 1);
                return;
            }
            purchaseRows.add(new PurchaseRow());
            pTable.getSelectionModel().select(purchaseRows.size() - 1);
            pTable.scrollTo(purchaseRows.size() - 1);
        }
    }

    // ---------- existing sum calculator / nav / save / delete (unchanged) ----------

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

    @FXML
    public void back(ActionEvent e) {
    	if (!confirmLeave()) return;
        Pages.change(e, Pages.SLSP);
    }

    @FXML
    public void SaveSlsp(ActionEvent e) {
    	persistSlsp();
        markClean();
        Pages.change(e, Pages.SLSP);
    }
    @FXML
    public void PrintSlsp(ActionEvent e) {
        if (!confirmLeave()) return;
        // TODO: print
    }

    @FXML
    public void DeleteSlsp(ActionEvent e) {
        // TODO
        Pages.change(e, Pages.SLSP);
    }

    @FXML
    public void Supplier(ActionEvent e) {
    	if (!confirmLeave()) return;
        Pages.change(e, Pages.SUPPLIER);
    }
    private void markDirty() {
        if (dirty) return;
        dirty = true;
        tradeNameText.setText(baseTitle + "*");
    }

    private void markClean() {
        dirty = false;
        tradeNameText.setText(baseTitle);
    }

    private void persistSlsp() {
        // TODO: persist saleRows / purchaseRows / month / year
    }

    /** Returns true if the caller may proceed (nothing to save, saved, or discarded). */
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
            persistSlsp();
            markClean();
        }
        return true; // "Don't Save" just proceeds
    }
}