package app.views;

import app.models.Supplier;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class SupplierCard extends VBox{
	@FXML private Text tinNumber;
	@FXML private Text tradeName;
	@FXML private Text businessAddress;
	
	public SupplierCard(Supplier supplier) {
		Pages.child(Pages.SUPPLIER_CARD, this, SupplierCard.class);
		tinNumber.setText(supplier.getTinNum());
		tradeName.setText(supplier.getTradeName());
		businessAddress.setText(supplier.getBussAddress());
	}
	
}
