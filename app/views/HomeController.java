package app.views;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import app.models.Account;
import app.models.PersonalInfo;
import app.models.Taxpayer;
import app.viewmodels.TaxpayerViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class HomeController {
	private TaxpayerViewModel vm;
	private List<Taxpayer> allTaxpayers = new ArrayList<>();

	@FXML private Text pageText;
	@FXML private GridPane business;
	@FXML private GridPane personalInfo;
	@FXML private GridPane account;
	@FXML private Button printBtn;
	@FXML private Button editBtn;
	@FXML private Button delBtn;
	@FXML private TextField searchField;
	@FXML private VBox taxpayerContainer;
	@FXML private FlowPane noSelectedHint;
	
	@FXML private void initialize(){
		try{
			noSelectedHint.setVisible(defTaxpayer.noSelected);
			editBtn.setDisable(defTaxpayer.noSelected);
			delBtn.setDisable(defTaxpayer.noSelected);
			if (!defTaxpayer.noSelected) loadTaxpayer(Taxpayer.getTaxpayer());

			vm = new TaxpayerViewModel();
			List<Taxpayer> taxpayers = vm.loadTaxpayers();
			allTaxpayers = taxpayers != null ? taxpayers : new ArrayList<>();
			renderTaxpayerCards(allTaxpayers);

			searchField.textProperty().addListener((_, _, newValue) -> filterTaxpayers(newValue));
		}catch(Exception e) {
			e.printStackTrace();
		}
	}

	private void renderTaxpayerCards(List<Taxpayer> taxpayers) {
		taxpayerContainer.getChildren().clear();
		for (Taxpayer taxpayer : taxpayers) {
			TaxpayerCard card = new TaxpayerCard(taxpayer);
			card.addEventHandler(MouseEvent.MOUSE_CLICKED, _ -> loadTaxpayer(taxpayer));
			taxpayerContainer.getChildren().add(card);
		}
	}

	private void filterTaxpayers(String query) {
		if (query == null || query.trim().isEmpty()) {
			renderTaxpayerCards(allTaxpayers);
			return;
		}
		String q = query.trim().toLowerCase();
		List<Taxpayer> filtered = allTaxpayers.stream()
				.filter(t -> matches(t, q))
				.collect(Collectors.toList());
		renderTaxpayerCards(filtered);
	}

	private boolean matches(Taxpayer taxpayer, String query) {
		String tinDigitsOnly = taxpayer.getTinNum() != null
				? taxpayer.getTinNum().replace("-", "").toLowerCase()
				: "";
		String queryDigitsOnly = query.replace("-", "");

		return containsIgnoreCase(taxpayer.getTaxName(), query)
				|| containsIgnoreCase(taxpayer.getTinNum(), query)
				|| tinDigitsOnly.contains(queryDigitsOnly)
				|| containsIgnoreCase(taxpayer.getTradeName(), query)
				|| containsIgnoreCase(taxpayer.getBussAddress(), query);
	}

	private boolean containsIgnoreCase(String source, String query) {
		return source != null && source.toLowerCase().contains(query);
	}

	private void loadTaxpayer(Taxpayer taxpayer) {
		Taxpayer.setTaxpayer(taxpayer);
		defTaxpayer.noSelected = false;
		noSelectedHint.setVisible(defTaxpayer.noSelected);
		editBtn.setDisable(defTaxpayer.noSelected);
		delBtn.setDisable(defTaxpayer.noSelected);

		PersonalInfo person = taxpayer.getPersonalInfo();
		Account acc = taxpayer.getAccount();
		tinNum.setText(taxpayer.getTinNum());
		fullName.setText(taxpayer.getTaxName());
		tradeName.setText(taxpayer.getTradeName());
		bussAddress.setText(taxpayer.getBussAddress());
		bussKind.setText(taxpayer.getBussKind());
		bussLine.setText(taxpayer.getBussLine());
		taxNformTypes.setText(taxpayer.getTaxNformTypes());
		psic.setText(taxpayer.getPsic());
		
		bday.setText(person.getBirthdate());
		bplace.setText(person.getBirthplace());
		civilStatus.setText(person.getCivilStatus());
		residence.setText(person.getResidence());
		tinSpouse.setText(person.getSpouseTin());
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
	}
	
	
	@FXML
	public void Logout(ActionEvent e) {
		Pages.change(e, Pages.AUTH);
	}
	@FXML
	public void AddTaxpayer(ActionEvent e) {
		Pages.change(e,Pages.ADD_TAXPAYER);
	}
	@FXML
	public void EditTaxpayer(ActionEvent e) {
		Pages.change(e,Pages.EDIT_TAXPAYER);
	}
	@FXML
	public void DeleteTaxpayer(ActionEvent e) {
		DeleteTaxpayer deleteView = new DeleteTaxpayer(Taxpayer.getTaxpayer().getId());
		Pages.popupComponent(deleteView);
		defTaxpayer.noSelected=true;
		Taxpayer.setTaxpayer(null);
		initialize();
	}
	private static class defTaxpayer{
		private static boolean noSelected=true;
	}
	@FXML private TextField tinNum;
	@FXML private TextField fullName;
	@FXML private TextField tradeName;
	@FXML private TextField bussAddress;
	@FXML private TextField bussKind;
	@FXML private TextField bussLine;
	@FXML private TextField taxNformTypes;
	@FXML private TextField psic;

	@FXML private TextField bday;
	@FXML private TextField bplace;
	@FXML private TextField civilStatus;
	@FXML private TextField residence;
	@FXML private TextField tinSpouse;
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
}