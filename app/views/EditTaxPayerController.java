package app.views;

import java.time.LocalDate;

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
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
public class EditTaxPayerController {
	private TaxpayerViewModel vm;
	private Taxpayer originalTaxpayer;
	@FXML private BorderPane root;
	@FXML
	public void initialize(){
		Pages.bindShortcut(root,"ESC",this::cancel);
		Pages.bindShortcut(root,"Ctrl+Enter",this::edit);
		
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
		try {
			vm=new TaxpayerViewModel();
			originalTaxpayer=Taxpayer.getTaxpayer();
			Name name = originalTaxpayer.getName();
			PersonalInfo person = originalTaxpayer.getPersonalInfo();
			Account acc = originalTaxpayer.getAccount();
			String[] tin = new String[4];
			tin = originalTaxpayer.getTinNum().split("-");
			tinNum1.setText(tin[0]);
			tinNum2.setText(tin[1]);
			tinNum3.setText(tin[2]);
			tinNum4.setText(tin[3]);
			lastName.setText(name.getLastName());
			firstName.setText(name.getFirstName());
			middleName.setText(name.getMiddleName());
			String suf = name.getSuffix();
			suffix.setValue(suf != null && !suf.trim().isEmpty() ? suf : "N/A");
			tradeName.setText(originalTaxpayer.getTradeName());
			bussAddress.setText(originalTaxpayer.getBussAddress());
			bussKind.setText(originalTaxpayer.getBussKind());
			formTypes.setText(originalTaxpayer.getFormTypes());
			String vat = originalTaxpayer.getVat();
			taxTypes.setValue(vat != null && !vat.trim().isEmpty() ? vat : "N/A");
			psic.setText(originalTaxpayer.getPsic());
			bday.setValue(person.getBirthdate() != null && !person.getBirthdate().trim().isEmpty()?LocalDate.parse(person.getBirthdate()):null);
			bplace.setText(person.getBirthplace());
			String status = person.getCivilStatus();
			civilStatus.setValue(status != null && !status.trim().isEmpty() ? status : "N/A");
			residence.setText(person.getResidence());
			if (person.getSpouseTin() != null && !person.getSpouseTin().trim().isEmpty()) {
			    String[] spouseTin = person.getSpouseTin().split("-");
			    if (spouseTin.length > 0) tinSpouse1.setText(spouseTin[0]);
			    if (spouseTin.length > 1) tinSpouse2.setText(spouseTin[1]);
			    if (spouseTin.length > 2) tinSpouse3.setText(spouseTin[2]);
			    if (spouseTin.length > 3) tinSpouse4.setText(spouseTin[3]);
			} else {
			    tinSpouse1.clear();
			    tinSpouse2.clear();
			    tinSpouse3.clear();
			    tinSpouse4.clear();
			}
			spouseName.setText(person.getSpouseName());
			fatherName.setText(person.getFatherName());
			motherName.setText(person.getMotherMaidenName());
			cpNum.setText(person.getCpNum());
			gmailEmail.setText(acc.getGmailEmail());
			gmailPass.setText(acc.getGmailPass());
			yahooEmail.setText(acc.getYahooEmail());
			yahooPass.setText(acc.getYahooPass());
			orusUser.setText(acc.getOrusName());
			orusPass.setText(acc.getOrusPass());
			afsUser.setText(acc.getAfsName());
			afsPass.setText(acc.getAfsPass());
			fbName.setText(acc.getFbName());
			recoveryEmail.setText(acc.getRecoveryEmail());
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		setUpSmartField(tinNum1,tinNum2,tinNum3,tinNum4);
		setUpSmartField(tinSpouse1,tinSpouse2,tinSpouse3,tinSpouse4);
		setUpTextOnly(lastName);
		setUpTextOnly(firstName);
		setUpTextOnly(middleName);
		setUpTextOnly(bussKind);
		setUpTextOnly(bplace);
		setUpTextOnly(spouseName);
		setUpTextOnly(fatherName);
		setUpTextOnly(motherName);
		cpNum.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty())return change;
            if (newText.matches("^\\+$|^\\+6$|^\\+63\\d{0,10}$"))return change;
            if (newText.matches("^\\d{1,4}$|^\\d{4}-$|^\\d{4}-\\d{1,3}$|^\\d{4}-\\d{3}-$|^\\d{4}-\\d{3}-\\d{1,4}$"))return change;
            if (newText.matches("^\\d{1,11}$"))return change;
            return null;
        }));
		
	}
	private void setUpTextOnly(TextField t) {
		t.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.matches("[a-zA-Z,\\.\\- ]*")) {
                return change;
            }
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
	@FXML
	public void cancel(ActionEvent e) {
		Pages.change(e,Pages.HOME);
	}
	@FXML
	public void edit(ActionEvent e){
		String tinN1 = tinNum1.getText().trim(),
		        tinN2 = tinNum2.getText().trim(),
		        tinN3 = tinNum3.getText().trim(),
		        tinN4 = tinNum4.getText().trim(),
		        lName = lastName.getText().trim(),
		        fName = firstName.getText().trim(),
		        mName = middleName.getText().trim(),
		        suf = suffix.getValue() != null && !suffix.getValue().equals("N/A") ? suffix.getValue() : null,
		        tName = tradeName.getText().trim(),
		        bAddress = bussAddress.getText().trim(),
		        rEmail = recoveryEmail.getText().toLowerCase().trim(),
		        sTin1 = tinSpouse1.getText().trim(),
		        sTin2 = tinSpouse2.getText().trim(),
		        sTin3 = tinSpouse3.getText().trim(),
		        sTin4 = tinSpouse4.getText().trim(),
		        cpNumber = cpNum.getText().trim(),
		        bdayText = bday.getEditor().getText().trim();

		boolean t1 = tinN1.isEmpty() || tinN1.length() < 3,
		        t2 = tinN2.isEmpty() || tinN2.length() < 3,
		        t3 = tinN3.isEmpty() || tinN3.length() < 3,
		        t4 = tinN4.isEmpty() || tinN4.length() < 3,
		        lNameError = lName.isEmpty(),
		        fNameError = fName.isEmpty(),
		        tradeNameError = tName.isEmpty(),
		        bussAddressError = bAddress.isEmpty();
		boolean recEmailError = !rEmail.isEmpty() && !rEmail.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
		boolean spouseTinIsBeingFilled = !sTin1.isEmpty() || !sTin2.isEmpty() || !sTin3.isEmpty() || !sTin4.isEmpty();
		boolean st1 = spouseTinIsBeingFilled && sTin1.length() < 3,
		        st2 = spouseTinIsBeingFilled && sTin2.length() < 3,
		        st3 = spouseTinIsBeingFilled && sTin3.length() < 3,
		        st4 = spouseTinIsBeingFilled && sTin4.length() < 3,
		        spouseTinError = st1 || st2 || st3 || st4;
		boolean cpNumError = false;
		if (!cpNumber.isEmpty()) {
		    boolean matchesInternational = cpNumber.matches("^\\+63\\d{10}$");
		    boolean matchesHyphenated = cpNumber.matches("^\\d{4}-\\d{3}-\\d{4}$");
		    boolean matchesLocal = cpNumber.matches("^09\\d{9}$") || cpNumber.matches("^\\d{11}$");
		    
		    if (!matchesInternational && !matchesHyphenated && !matchesLocal) {
		        cpNumError = true;
		    }
		}
		boolean bdayError = !bdayText.isEmpty() && !bdayText.matches("^(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01])/\\d{4}$");

		String errors = "";
		if (t1 || t2 || t3 || t4) errors += "• Complete the TIN Number.\n";
		if (lNameError || fNameError) errors += "• Last Name and First Name are required.\n";
		if (tradeNameError) errors += "• Trade Name is required.\n";
		if (bussAddressError) errors += "• Business Address is required.\n";
		if(bdayError) errors += "• Birthday must follow the MM/DD/YYYY format.\n";
		if (spouseTinError) errors += "• Complete the Spouse TIN Number.\n";
		if (cpNumError) errors += "• Invalid CP Number format\n";
		if (recEmailError) errors += "• Invalid email format.\n";
		
		errorTextField(tinNum1, t1);
		errorTextField(tinNum2, t2);
		errorTextField(tinNum3, t3);
		errorTextField(tinNum4, t4);
		errorTextField(lastName, lNameError);
		errorTextField(firstName, fNameError);
		errorTextField(tradeName, tradeNameError);
		errorTextField(bussAddress, bussAddressError);
		errorTextField(recoveryEmail, recEmailError);
		errorTextField(tinSpouse1, st1);
		errorTextField(tinSpouse2, st2);
		errorTextField(tinSpouse3, st3);
		errorTextField(tinSpouse4, st4);
		errorTextField(cpNum, cpNumError);
		errorTextField(bday,bdayError);

		if (t1 || t2 || t3 || t4 || lNameError || fNameError || tradeNameError || 
		    bussAddressError || recEmailError || spouseTinError || cpNumError||bdayError) {
		    errorText.setText(errors.trim());
		    return;
		}

		Taxpayer taxpayer=new Taxpayer();
		Name name= new Name();
		PersonalInfo personalInfo = new PersonalInfo();
		Account account = new Account();

		taxpayer.setId(originalTaxpayer.getId());
		name.setId(originalTaxpayer.getName().getId());
		name.setTaxId(originalTaxpayer.getId());
		personalInfo.setId(originalTaxpayer.getPersonalInfo().getId());
		personalInfo.setTaxId(originalTaxpayer.getId());
		account.setId(originalTaxpayer.getAccount().getId());
		account.setTaxId(originalTaxpayer.getId());
		taxpayer.setSuppliers(originalTaxpayer.getSuppliers());

		name.setLastName(lName);
		name.setFirstName(fName);
		name.setMiddleName(mName);
		name.setSuffix(suf);
		taxpayer.setTinNum(tinN1+"-"+tinN2+"-"+tinN3+"-"+tinN4);
		taxpayer.setTradeName(tName);
		taxpayer.setBussAddress(bAddress);
		taxpayer.setBussKind(bussKind.getText().trim());
		taxpayer.setFormTypes(formTypes.getText().trim());
		taxpayer.setVat(taxTypes.getValue()!=null && !taxTypes.getValue().equals("N/A") ?taxTypes.getValue():null);
		taxpayer.setPsic(psic.getText().trim());
		personalInfo.setBirthdate(bday.getValue()!=null? bday.getValue().toString().trim():null);
		personalInfo.setBirthplace(bplace.getText().trim());
		personalInfo.setCivilStatus(civilStatus.getValue()!=null && !civilStatus.getValue().equals("N/A") ?civilStatus.getValue():null);
		personalInfo.setResidence(residence.getText().trim());
		personalInfo.setSpouseTin(sTin1+"-"+sTin2+"-"+sTin3+"-"+sTin4);
		personalInfo.setSpouseName(spouseName.getText().trim());
		personalInfo.setFatherName(fatherName.getText().trim());
		personalInfo.setMotherMaidenName(motherName.getText().trim());
		personalInfo.setCpNum(cpNumber);
		account.setGmailEmail(gmailEmail.getText().trim().toLowerCase());
		account.setGmailPass(gmailPass.getText());
		account.setYahooEmail(yahooEmail.getText().trim().toLowerCase());
		account.setYahooPass(yahooPass.getText());
		account.setOrusName(orusUser.getText().trim());
		account.setOrusPass(orusPass.getText());
		account.setAfsName(afsUser.getText().trim());
		account.setAfsPass(afsPass.getText());
		account.setFbName(fbName.getText().trim());
		account.setRecoveryEmail(rEmail);
		taxpayer.setName(name);
		taxpayer.setPersonalInfo(personalInfo);
		taxpayer.setAccount(account);
		try {
			vm.updateTaxpayer(taxpayer);
			Taxpayer refreshed = vm.loadTaxpayers().stream()
		            .filter(t -> t.getId() == taxpayer.getId())
		            .findFirst()
		            .orElse(taxpayer);
		    Taxpayer.setTaxpayer(refreshed);
			Pages.change(e, Pages.HOME);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}
	
	private void errorTextField(TextField t,boolean hasError) {
		t.setStyle((hasError)?"""
				-fx-border-color:red;
				-fx-border-radius:10;
				-fx-background-radius:10
				""":"""
					-fx-border-color:none;
					-fx-border-radius:10;
					-fx-background-radius:10
					""");
	}
	private void errorTextField(DatePicker t,boolean hasError) {
		t.setStyle((hasError)?"""
				-fx-border-color:red;
				-fx-border-radius:10;
				-fx-background-radius:10
				""":"""
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
		@FXML private  TextField formTypes;
		@FXML private  ComboBox<String> taxTypes;
		@FXML private  TextField psic;
	
		@FXML private DatePicker bday;
		@FXML private TextField bplace;
		@FXML private ComboBox<String> civilStatus;
		@FXML private TextField residence;
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