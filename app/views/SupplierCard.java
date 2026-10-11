package app.views;

import app.models.Supplier;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class SupplierCard extends VBox {
    private static final String SELECTED_STYLE =
            "-fx-background-color: #dbeafe; -fx-background-radius: 8;"
          + "-fx-border-color: #2563eb; -fx-border-width: 2; -fx-border-radius: 8;";

    @FXML private Text tinNumber;
    @FXML private Text tradeName;
    @FXML private Text businessAddress;
    private final Runnable onDeleteRequested;
    private final Supplier supplier;
    private final String baseStyle;

    public SupplierCard(Supplier supplier, Runnable onDeleteRequested) {
        Pages.child(Pages.SUPPLIER_CARD, this, SupplierCard.class);
        this.onDeleteRequested = onDeleteRequested;
        this.supplier = supplier;
        this.baseStyle = getStyle() == null ? "" : getStyle();
        tinNumber.setText(supplier.getTinNum());
        tradeName.setText(supplier.getTradeName());
        businessAddress.setText(supplier.getBussAddress());
    }

    public Supplier getSupplier() { return supplier; }

    public void setSelected(boolean selected) {
        setStyle(selected ? baseStyle + SELECTED_STYLE : baseStyle);
    }

    @FXML
    public void DeleteSupplier(ActionEvent e) {
        onDeleteRequested.run();
    }
}