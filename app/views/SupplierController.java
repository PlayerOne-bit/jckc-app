package app.views;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

public class SupplierController {
	@FXML private TextField tin1;
	@FXML private TextField tin2;
	@FXML private TextField tin3;
	@FXML private TextField tin4;
	@FXML private TextField tradeName;
	@FXML private TextField bussAddress;
	@FXML
	public void CreateSupplier(ActionEvent e) {
		
	}
	@FXML
	public void back(ActionEvent e) {
		Pages.change(e, Pages.SLSP_MANAGER);
	}
}
