package app.views;

import app.models.Account;
import app.models.Name;
import app.models.PersonalInfo;
import app.models.Taxpayer;
import app.viewmodels.TaxpayerViewModel;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Text;
public class AddTaxPayerController {
	private TaxpayerViewModel viewModel; 
	
	@FXML
	public void initialize(){
		try {
			viewModel=new TaxpayerViewModel();
		} catch (Exception e) {
			e.printStackTrace();
		}
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
	public void cancel(ActionEvent e) {
		Pages.change(e,Pages.HOME);
	}
	
	public void create(ActionEvent e){
		String tinN1 = tinNum1.getText().trim();
		String tinN2 = tinNum2.getText().trim();
		String tinN3 = tinNum3.getText().trim();
		String tinN4 = tinNum4.getText().trim();
		String lName=lastName.getText().trim();
		String fName = firstName.getText().trim();
		String mName = middleName.getText().trim();
		String suf = !suffix.getValue().equals("N/A")?suffix.getValue():null;
		String tName =tradeName.getText().trim();
		String bAddress = bussAddress.getText().trim();
		
		boolean t1=tinN1.isEmpty()||tinN1.length()<3,
				t2=tinN2.isEmpty()||tinN2.length()<3,
				t3=tinN3.isEmpty()||tinN3.length()<3,
				t4=tinN4.isEmpty()||tinN4.length()<3,
				lNameError=lName.isEmpty(),
				fNameError=fName.isEmpty(),
				bussAddressError=bAddress.isEmpty(),
				tradeNameError=tName.isEmpty();
		String errors ="";
		if(t1||t2||t3||t4) errors+="Complete the Tin Number.";
		if(lNameError || fNameError)errors+=" Last Name and First Name is required.";
		if(tradeNameError)	errors+="\nTrade Name is required.";
		if(bussAddressError)errors+=" Business Address is required.";
		errorTextField(tinNum1,t1);
		errorTextField(tinNum2,t2);
		errorTextField(tinNum3,t3);
		errorTextField(tinNum4,t4);
		errorTextField(lastName,lNameError);
		errorTextField(firstName,fNameError);
		errorTextField(tradeName,tradeNameError);
		errorTextField(bussAddress,bussAddressError);
		if(t1||t2||t3||t4||lNameError||fNameError||tradeNameError||bussAddressError) {
			errorText.setText(errors);
			return;
		}
		Taxpayer taxpayer=new Taxpayer();
		Name name= new Name();
		PersonalInfo personalInfo = new PersonalInfo();
		Account account = new Account();
		taxpayer.setTinNum(tinN1+"-"+tinN2+"-"+tinN3+"-"+tinN4);
		name.setLastName(lName);
		name.setFirstName(fName);
		name.setMiddleName(mName);
		name.setSuffix(suf);
		taxpayer.setTradeName(tName);
		taxpayer.setBussAddress(bAddress);
		taxpayer.setBussKind(bussKind.getText().trim());
		taxpayer.setBussLine( bussLine.getText().trim());
		taxpayer.setTaxNformTypes(formTypes.getText().trim());
		taxpayer.setVat(!taxTypes.getValue().equals("N/A")?taxTypes.getValue():null);
		taxpayer.setPsic(psic.getText().trim());
		personalInfo.setBirthdate(bday.getValue()!=null? bday.getValue().toString().trim():null);
		personalInfo.setBirthplace(bplace.getText().trim());
		personalInfo.setCivilStatus(!civilStatus.getValue().equals("N/A")?civilStatus.getValue():null);
		personalInfo.setSpouseTin(tinSpouse1.getText()+"-"+tinSpouse2.getText()+"-"+tinSpouse3.getText()+"-"+tinSpouse4.getText());
		personalInfo.setSpouseName(spouseName.getText().trim());
		personalInfo.setFatherName(fatherName.getText().trim());
		personalInfo.setMotherMaidenName(motherName.getText().trim());
		personalInfo.setCpNum(cpNum.getText());
		account.setGmailEmail(gmailEmail.getText().trim().toLowerCase());
		account.setGmailPass(gmailPass.getText());
		account.setYahooEmail(yahooEmail.getText().trim().toLowerCase());
		account.setYahooPass(yahooPass.getText());
		account.setOrusName(orusUser.getText().trim());
		account.setOrusPass(orusPass.getText());
		account.setAfsName(afsUser.getText().trim());
		account.setAfsPass(afsPass.getText());
		account.setFbName(fbName.getText().trim());
		account.setRecoveryEmail(recoveryEmail.getText().toLowerCase().trim());
		taxpayer.setName(name);
		taxpayer.setPersonalInfo(personalInfo);
		taxpayer.setAccount(account);
		try {
			viewModel.createTaxpayer(taxpayer);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}
	
	private void errorTextField(TextField t,boolean hasError) {
		if(hasError) {
		t.setStyle("""
				-fx-border-color:red;
				-fx-border-radius:10;
				-fx-background-radius:10
				""");
			return;
		}
		t.setStyle("""
				-fx-border-color:none;
				-fx-border-radius:10;
				-fx-background-radius:10
				""");
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
