package app.views;

import app.viewmodels.AuthAdmin;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;

public class AuthController {
	private int errors=0;
	@FXML 
	private PasswordField authField;
	
	@FXML 
	private Text errorAuthText;
	@FXML
	private void handleAuthField(KeyEvent event) {
	    if (event.getCode() == KeyCode.ENTER) {
	        Login(new ActionEvent(event.getSource(),event.getTarget())); 
	    }
	}
	@FXML
    public void Login(ActionEvent event) {
        errorAuthText.setText("");
        String input = authField.getText();
        if (input == null || input.trim().isEmpty()) {
        	errors++;
            errorAuthText.setText("Password field cannot be empty."+(errors>1?" ("+errors+")":""));
            return;
        }
        boolean success = AuthAdmin.isPasswordCorrect(input);
        if (success) {
            Pages.change(event, Pages.HOME);
        } else {
        	errors++;
        	errorAuthText.setText("Incorrect password. Please try again."+(errors>1?" ("+errors+")":""));
            authField.clear(); 
        }
	}
}
