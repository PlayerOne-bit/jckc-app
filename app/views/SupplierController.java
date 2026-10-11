package app.views;

import java.util.ArrayList;
import java.util.List;

import app.models.Supplier;
import app.models.Taxpayer;
import app.viewmodels.SupplierViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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

    private final List<SupplierCard> cards = new ArrayList<>();
    private Supplier selected;          // the highlighted supplier, or null when the form is in "new" mode
    private boolean filling = false;    // true while we set the fields from code, so focus doesn't jump

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
        Pages.bindShortcut(root, "Ctrl+W", _ -> deleteSelected());

        root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (ev.isControlDown() || ev.isAltDown() || ev.isShiftDown()) return;
            if (ev.getCode() == KeyCode.DOWN) {
                moveSelection(1);
                ev.consume();
            } else if (ev.getCode() == KeyCode.UP) {
                moveSelection(-1);
                ev.consume();
            }
        });

        vm = new SupplierViewModel();
        setUpSmartField(tin1, tin2, tin3, tin4);
        renderSuppliers();
    }

    // ---------- Cards and selection ----------

    private void renderSuppliers() {
        List<Supplier> suppliers = vm.loadSuppliers();
        supplierContainer.getChildren().clear();
        cards.clear();
        if (suppliers == null) return;
        for (Supplier supplier : suppliers) {
            SupplierCard card = new SupplierCard(supplier, () -> onDeleteRequested(supplier));
            card.setOnMouseClicked(_ -> {
                int i = indexOfId(supplier.getId());   // by id, so a just-deleted card is ignored
                if (i >= 0) select(i);
            });
            cards.add(card);
            supplierContainer.getChildren().add(card);
        }
        if (selected != null && indexOfId(selected.getId()) < 0) selected = null;
        highlight();
    }

    private int indexOfId(int id) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).getSupplier().getId() == id) return i;
        }
        return -1;
    }

    private void moveSelection(int delta) {
        if (cards.isEmpty()) return;
        int current = selected == null ? -1 : indexOfId(selected.getId());
        int next = current < 0
                ? (delta > 0 ? 0 : cards.size() - 1)
                : Math.max(0, Math.min(cards.size() - 1, current + delta));
        select(next);
    }

    private void select(int index) {
        selected = cards.get(index).getSupplier();
        highlight();
        fillFields(selected);
        scrollTo(cards.get(index));
    }

    private void highlight() {
        int idx = selected == null ? -1 : indexOfId(selected.getId());
        for (int i = 0; i < cards.size(); i++) cards.get(i).setSelected(i == idx);
    }

    private void scrollTo(SupplierCard card) {
        Node n = supplierContainer;
        while (n != null && !(n instanceof ScrollPane)) n = n.getParent();
        if (n instanceof ScrollPane sp) {
            double content = supplierContainer.getBoundsInLocal().getHeight();
            double view = sp.getViewportBounds().getHeight();
            if (content > view) {
                double mid = card.getBoundsInParent().getMinY() + card.getBoundsInParent().getHeight() / 2;
                sp.setVvalue(Math.max(0, Math.min(1, (mid - view / 2) / (content - view))));
            }
        }
    }

    // ---------- Form ----------

    private void fillFields(Supplier s) {
        filling = true;
        try {
            String[] parts = s.getTinNum() == null ? new String[0] : s.getTinNum().split("-");
            tin1.setText(parts.length > 0 ? parts[0] : "");
            tin2.setText(parts.length > 1 ? parts[1] : "");
            tin3.setText(parts.length > 2 ? parts[2] : "");
            tin4.setText(parts.length > 3 ? parts[3] : "");
            tradeName.setText(s.getTradeName() == null ? "" : s.getTradeName());
            bussAddress.setText(s.getBussAddress() == null ? "" : s.getBussAddress());
        } finally {
            filling = false;
        }
        resetErrors();
    }

    private void clearForm() {
        filling = true;
        try {
            tin1.clear(); tin2.clear(); tin3.clear(); tin4.clear();
            tradeName.clear();
            bussAddress.clear();
        } finally {
            filling = false;
        }
        resetErrors();
    }

    private void resetErrors() {
        for (TextField t : new TextField[] { tin1, tin2, tin3, tin4, tradeName, bussAddress }) {
            errorTextField(t, false);
        }
        tinNumErrorText.setText("");
        tradeNameErrorText.setText("");
        businessAddressErrorText.setText("");
    }

    // ---------- Delete ----------

    private void deleteSelected() {
        if (selected != null) onDeleteRequested(selected);
    }

    private void onDeleteRequested(Supplier supplier) {
        try {
            boolean deleted = vm.deleteSupplier(supplier.getId());
            if (!deleted) {
                showAlert("Cannot delete supplier",
                        "\"" + supplier.getTradeName() + "\" is used in an existing purchase and cannot be deleted.");
                return;
            }
            if (selected != null && selected.getId() == supplier.getId()) {
                selected = null;
                clearForm();
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

    // ---------- TIN fields ----------

    private void setupSmartField(TextField current, TextField next, TextField previous) {
        current.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.matches("\\d*") && newText.length() <= 3) {
                return change;
            }
            return null;
        }));
        current.textProperty().addListener((_, _, newValue) -> {
            if (filling) return;   // don't jump focus while the fields are filled from a card
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

    // ---------- Create / edit ----------

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

        // New TIN -> created (same instance comes back). Existing TIN -> that supplier is updated.
        Supplier saved = vm.saveSupplier(supplier);
        boolean created = saved == supplier;

        if (created) {
            selected = null;
            clearForm();
        } else {
            selected = saved;   // stay on the edited supplier, form keeps its values
        }
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