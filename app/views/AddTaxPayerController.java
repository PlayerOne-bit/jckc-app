package app.views;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;

public class AddTaxPayerController {
	
	
	
	@FXML
	public void initialize(){
		TaxpayerTab.suffix.setItems(FXCollections.observableArrayList(
				"Jr.",
				"Sr.",
				"II",
				"III",
				"IV"
				));
	}
	
	public void back(ActionEvent e) {
		Pages.change(e,Pages.HOME);
	}
	
	public void save(ActionEvent e) {
		
	}
	
	private class TaxpayerTab{
		@FXML private static TextField tinNum1;
		@FXML private static TextField tinNum2;
		@FXML private static TextField tinNum3;
		@FXML private static TextField tinNum4;
		@FXML private static TextField lastName;
		@FXML private static TextField firstName;
		@FXML private static TextField middleName;
		@FXML private static ComboBox<String> suffix;
		@FXML private static TextField tradeName;
		@FXML private static TextField bussAddress;
		@FXML private static TextField bussKind;
		@FXML private static TextField bussLine;
		@FXML private static TextField formTypes;
		@FXML private static ComboBox<String> taxTypes;
		@FXML private static TextField psic;
	}
	private class PersonalInfoTab{
		@FXML private static DatePicker bday;
		@FXML private static TextField bplace;
		@FXML private static ComboBox<String> civilStatus; 
		@FXML private static TextField tinSpouse1;
		@FXML private static TextField tinSpouse2;
		@FXML private static TextField tinSpouse3;
		@FXML private static TextField tinSpouse4;
		@FXML private static TextField spouseName;
		@FXML private static TextField fatherName;
		@FXML private static TextField motherName;			
	}
	private class AccountsTabs{
		@FXML private static TextField gmailEmail;
		@FXML private static TextField gmailPass;
		@FXML private static TextField yahooEmail;
		@FXML private static TextField yahooPass;
		@FXML private static TextField orusUser;
		@FXML private static TextField orusPass;
		@FXML private static TextField afsUser;
		@FXML private static TextField afsPass;
		@FXML private static TextField fbName;
		@FXML private static TextField recoveryEmail;
	}
}
