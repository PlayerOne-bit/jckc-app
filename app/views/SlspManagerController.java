package app.views;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.BorderPane;

public class SlspManagerController {
	@FXML private ComboBox<String> years;
	@FXML private ComboBox<String> months;
	@FXML private BorderPane root;
	
	@FXML
	private void initialize() {
		Pages.bindShortcut(root,"Ctrl+S",this::SaveSlsp);
		Pages.bindShortcut(root,"Ctrl+D",this::DeleteSlsp);
		Pages.bindShortcut(root,"ESC",this::back);
		
	}
	
	@FXML
	public void back(ActionEvent e) {
		Pages.change(e, Pages.SLSP);
	}
	@FXML
	public void SaveSlsp(ActionEvent e) {
		Pages.change(e,Pages.SLSP);
	}
	@FXML
	public void DeleteSlsp(ActionEvent e) {
		Pages.change(e,Pages.SLSP);
	}
}
