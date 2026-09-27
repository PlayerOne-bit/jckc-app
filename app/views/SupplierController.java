package app.views;

import java.util.List;

import app.models.Supplier;
import app.models.Taxpayer;
import app.viewmodels.SupplierViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class SupplierController {
    @FXML private TextField tin1;
    @FXML private TextField tin2;
    @FXML private TextField tin3;
    @FXML private TextField tin4;
    @FXML private TextField tradeName;
    @FXML private TextField bussAddress;
    @FXML private Text tinNumErrorText;
    @FXML private Text tradeNameErrorText;
    @FXML private Text businessAddressErrorText;
    @FXML private VBox supplierContainer;
    @FXML private BorderPane root;
    private SupplierViewModel vm;

    @FXML
    private void initialize() throws Exception {
        Pages.bindShortcut(root, "ESC", this::back);
        Pages.bindShortcut(root, "Ctrl+Enter", t -> {
			try {
				CreateSupplier(t);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
        vm = new SupplierViewModel();
        setUpSmartField(tin1, tin2, tin3, tin4);
        renderSuppliers();
    }

    private void renderSuppliers() {
        List<Supplier> suppliers = vm.loadSuppliers();
        supplierContainer.getChildren().clear();
        if (suppliers == null) return;
        for (Supplier supplier : suppliers) {
            SupplierCard card = new SupplierCard(supplier, () -> onDeleteRequested(supplier));
            supplierContainer.getChildren().add(card);
        }
    }

    private void onDeleteRequested(Supplier supplier) {
        try {
            boolean deleted = vm.deleteSupplier(supplier.getId());
            if (!deleted) {
                showAlert("Cannot delete supplier",
                        "\"" + supplier.getTradeName() + "\" is used in an existing purchase and cannot be deleted.");
                return;
            }
            renderSuppliers();
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert("Error", "Something went wrong while deleting the supplier.");
        }
    }

    private void showAlert(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void setupSmartField(TextField current, TextField next, TextField previous) {
        current.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.matches("\\d*") && newText.length() <= 3) {
                return change;
            }
            return null;
        }));
        current.textProperty().addListener((_, _, newValue) -> {
            if (newValue.length() == 3 && next != null) {
                next.requestFocus();
                next.selectAll();
            }
        });
        current.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.BACK_SPACE && current.getText().isEmpty() && previous != null) {
                previous.requestFocus();
                previous.positionCaret(previous.getText().length());
            }
        });
    }

    private void setUpSmartField(TextField txtField1, TextField txtField2, TextField txtField3, TextField txtField4) {
        setupSmartField(txtField1, txtField2, null);
        setupSmartField(txtField2, txtField3, txtField1);
        setupSmartField(txtField3, txtField4, txtField2);
        setupSmartField(txtField4, null, txtField3);
    }

    @FXML
    public void CreateSupplier(ActionEvent e) throws Exception {
        String tinNum1 = tin1.getText(),
                tinNum2 = tin2.getText(),
                tinNum3 = tin3.getText(),
                tinNum4 = tin4.getText(),
                tin = tinNum1 + "-" + tinNum2 + "-" + tinNum3 + "-" + tinNum4,
                tName = tradeName.getText().trim(),
                bAddress = bussAddress.getText().trim();
        boolean tinError1 = tinNum1.isEmpty() || tinNum1.length() < 3,
                tinError2 = tinNum2.isEmpty() || tinNum2.length() < 3,
                tinError3 = tinNum3.isEmpty() || tinNum3.length() < 3,
                tinError4 = tinNum4.isEmpty() || tinNum4.length() < 3,
                tNameError = tName.isEmpty(),
                bAddressError = bAddress.isEmpty();

        errorTextField(tin1, tinError1);
        errorTextField(tin2, tinError2);
        errorTextField(tin3, tinError3);
        errorTextField(tin4, tinError4);
        errorTextField(tradeName, tNameError);
        errorTextField(bussAddress, bAddressError);

        tinNumErrorText.setText("");
        tradeNameErrorText.setText("");
        businessAddressErrorText.setText("");

        if (tinError1 || tinError2 || tinError3 || tinError4 || tNameError || bAddressError) {
            tinNumErrorText.setText("Complete the Tin Number.");
            tradeNameErrorText.setText("Trade Name is required.");
            businessAddressErrorText.setText("Business Address is Required");
            return;
        }

        Supplier supplier = new Supplier();
        supplier.setTaxId(Taxpayer.getTaxpayer().getId());
        supplier.setTinNum(tin);
        supplier.setTradeName(tName);
        supplier.setBussAddress(bAddress);

        String error = vm.createSupplier(supplier);
        if (error != null) {
            errorTextField(tin1, true);
            errorTextField(tin2, true);
            errorTextField(tin3, true);
            errorTextField(tin4, true);
            tinNumErrorText.setText(error);
            return;
        }

        tin1.clear(); tin2.clear(); tin3.clear(); tin4.clear();
        tradeName.clear();
        bussAddress.clear();
        renderSuppliers();
    }

    private void errorTextField(TextField t, boolean hasError) {
        t.setStyle((hasError) ? """
                -fx-border-color:red;
                -fx-border-radius:10;
                -fx-background-radius:10
                """ : """
                    -fx-border-color:none;
                    -fx-border-radius:10;
                    -fx-background-radius:10
                    """);
    }

    @FXML
    public void back(ActionEvent e) {
        Pages.change(e, Pages.SLSP_MANAGER);
    }
}