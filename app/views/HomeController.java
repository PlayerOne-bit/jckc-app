package app.views;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;

public class HomeController {
	
	
	@FXML private Text pageText;
	@FXML private GridPane business;
	@FXML private GridPane personalInfo;
	@FXML private GridPane account;
	@FXML private Button printBtn;
	@FXML
	public void Logout(ActionEvent event) {
		Pages.change(event, Pages.AUTH);
	}
	public void AddTaxpayer(ActionEvent event) {
		Pages.change(event,Pages.ADD_TAXPAYER);
	}
	
}
