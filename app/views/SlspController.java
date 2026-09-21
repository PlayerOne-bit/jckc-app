package app.views;

import app.models.Taxpayer;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

public class SlspController {
	@FXML private Label taxpayerName;
	@FXML private Label tinNumber;
	@FXML private Label tradeName;
	@FXML private BorderPane root;
	
	@FXML
	private void initialize() {
		Pages.bindShortcut(root, "Ctrl+N", this::AddSlsp);
		Pages.bindShortcut(root, "ESC", this::back);
		
		
		taxpayerName.setText(Taxpayer.getTaxpayer().getName().getFullName(1));
		tinNumber.setText(Taxpayer.getTaxpayer().getTinNum());
		tradeName.setText(Taxpayer.getTaxpayer().getTradeName());
	}
	
	@FXML
	public void back(ActionEvent e) {
		Pages.change(e,Pages.HOME);
	}
	
	@FXML
	public void AddSlsp(ActionEvent e) {
		Pages.change(e, Pages.SLSP_MANAGER);
	}
}
