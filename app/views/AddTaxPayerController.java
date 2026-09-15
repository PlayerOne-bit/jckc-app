package app.views;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Text;
public class AddTaxPayerController {
	
	
	
	@FXML
	public void initialize(){
		suffix.setItems(FXCollections.observableArrayList(
				"Jr.",
				"Sr.",
				"II",
				"III",
				"IV",
				"N/A"
				));
		taxTypes.setItems(FXCollections.observableArrayList(
				"VAT",
				"NON-VAT",
				"N/A"
				));
		civilStatus.setItems(FXCollections.observableArrayList(
				"Single",
				"Married",
				"Widowed",
				"Divorced",
				"Seperated",
				"N/A"
				));
		setUpSmartField(tinNum1,tinNum2,tinNum3,tinNum4);
		setUpSmartField(tinSpouse1,tinSpouse2,tinSpouse3,tinSpouse4);
		cpNum.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty())return change;
            if (newText.matches("^\\+$|^\\+6$|^\\+63\\d{0,10}$"))return change;
            if (newText.matches("^\\d{1,4}$|^\\d{4}-$|^\\d{4}-\\d{1,3}$|^\\d{4}-\\d{3}-$|^\\d{4}-\\d{3}-\\d{1,4}$"))return change;
            if (newText.matches("^\\d{1,11}$"))return change;
            return null;
        }));
		
	}
	private void setUpSmartField(TextField txtField1,TextField txtField2,TextField txtField3, TextField txtField4) {
		setupSmartField(txtField1, txtField2, null);
        setupSmartField(txtField2, txtField3, txtField1);
        setupSmartField(txtField3, txtField4, txtField2);
        setupSmartField(txtField4, null, txtField3);
	}
	private void setupSmartField(TextField current, TextField next, TextField previous) {
        current.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.matches("\\d*") && newText.length() <= 3) {
                return change;
            }
            return null;
        }));
        current.textProperty().addListener((_, _, newValue) -> {
            if (newValue.length() == 3 && next != null) {
                next.requestFocus();
                next.selectAll();
            }
        });
        current.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.BACK_SPACE && current.getText().isEmpty() && previous != null) {
                previous.requestFocus();
                previous.positionCaret(previous.getText().length()); 
            }
        });
    }
	
	public void back(ActionEvent e) {
		Pages.change(e,Pages.HOME);
	}
	
	public void save(ActionEvent e) {
		
	}
	
		@FXML private TextField tinNum1;
		@FXML private TextField tinNum2;
		@FXML private TextField tinNum3;
		@FXML private TextField tinNum4;
		@FXML private TextField lastName;
		@FXML private TextField firstName;
		@FXML private TextField middleName;
		@FXML private ComboBox<String> suffix;
		@FXML private  TextField tradeName;
		@FXML private  TextField bussAddress;
		@FXML private  TextField bussKind;
		@FXML private  TextField bussLine;
		@FXML private  TextField formTypes;
		@FXML private  ComboBox<String> taxTypes;
		@FXML private  TextField psic;
	
		@FXML private DatePicker bday;
		@FXML private TextField bplace;
		@FXML private ComboBox<String> civilStatus; 
		@FXML private TextField tinSpouse1;
		@FXML private TextField tinSpouse2;
		@FXML private TextField tinSpouse3;
		@FXML private TextField tinSpouse4;
		@FXML private TextField spouseName;
		@FXML private TextField fatherName;
		@FXML private TextField motherName;
		@FXML private TextField cpNum;
	
		@FXML private TextField gmailEmail;
		@FXML private TextField gmailPass;
		@FXML private TextField yahooEmail;
		@FXML private TextField yahooPass;
		@FXML private TextField orusUser;
		@FXML private TextField orusPass;
		@FXML private TextField afsUser;
		@FXML private TextField afsPass;
		@FXML private TextField fbName;
		@FXML private TextField recoveryEmail;
		
		@FXML private Text errorText;
}
