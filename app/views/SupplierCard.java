package app.views;

import app.models.Supplier;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class SupplierCard extends VBox {
    @FXML private Text tinNumber;
    @FXML private Text tradeName;
    @FXML private Text businessAddress;
    private final Runnable onDeleteRequested;

    public SupplierCard(Supplier supplier, Runnable onDeleteRequested) {
        Pages.child(Pages.SUPPLIER_CARD, this, SupplierCard.class);
        this.onDeleteRequested = onDeleteRequested;
        tinNumber.setText(supplier.getTinNum());
        tradeName.setText(supplier.getTradeName());
        businessAddress.setText(supplier.getBussAddress());
    }

    @FXML
    public void DeleteSupplier(ActionEvent e) {
        onDeleteRequested.run();
    }
}