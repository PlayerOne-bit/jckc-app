package app.views;

import app.models.Supplier;
import app.viewmodels.SupplierViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class SupplierCard extends VBox{
	@FXML private Text tinNumber;
	@FXML private Text tradeName;
	@FXML private Text businessAddress;
	private int id;
	public SupplierCard(Supplier supplier) {
		Pages.child(Pages.SUPPLIER_CARD, this, SupplierCard.class);
		this.id=supplier.getId();
		tinNumber.setText(supplier.getTinNum());
		tradeName.setText(supplier.getTradeName());
		businessAddress.setText(supplier.getBussAddress());
	}
	@FXML
	public void DeleteSupplier(ActionEvent e) throws Exception{
		SupplierViewModel vm = new SupplierViewModel();
		vm.deleteSupplier(id);
	}
}
