package app.views;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.kordamp.ikonli.javafx.FontIcon;

import app.models.Account;
import app.models.PersonalInfo;
import app.models.Taxpayer;
import app.viewmodels.TaxpayerViewModel;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class HomeController {
	private TaxpayerViewModel vm;
	private boolean isTaxpayerSelected;
	private List<Taxpayer> allTaxpayers = new ArrayList<>();
	private List<Taxpayer> displayedTaxpayers = new ArrayList<>(); 
	private final List<TaxpayerCard> cards = new ArrayList<>();
	private int selectedIndex = -1;
	
	
	@FXML private BorderPane root;
	@FXML private Text pageText;
	@FXML private GridPane business;
	@FXML private GridPane personalInfo;
	@FXML private GridPane account;
	@FXML private Button printBtn;
	@FXML private Button editBtn;
	@FXML private Button delBtn;
	@FXML private Button slspBtn;
	@FXML private Button confirmationBtn;
	@FXML private TextField searchField;
	@FXML private VBox taxpayerContainer;
	@FXML private FlowPane noSelectedHint;
	@FXML private FontIcon notify;
	
	@FXML private void initialize(){
		try{
			Pages.bindShortcut(root,"Ctrl+F",searchField);
			Pages.bindShortcut(root,"Ctrl+N",this::AddTaxpayer);
			Pages.bindShortcut(root,"Ctrl+E",this::EditTaxpayer);
			Pages.bindShortcut(root,"Ctrl+D",this::DeleteTaxpayer);
			Pages.bindShortcut(root, "Ctrl+P", this::PrintTaxpayer);
			Pages.bindShortcut(root,"ESC",this::Logout);
			Pages.bindShortcut(root,"UP",this::SelectPrevious);
			Pages.bindShortcut(root,"DOWN",this::SelectNext);
			Pages.bindShortcut(root, "F2", this::SLSP);
			
			if (this.isTaxpayerSelected) loadTaxpayer(Taxpayer.getTaxpayer());
			togglePassBtn.setSelected(false);
			gmailPass.setVisible(togglePassBtn.isSelected());
			yahooPass.setVisible(togglePassBtn.isSelected());
			orusPass.setVisible(togglePassBtn.isSelected());
			afsPass.setVisible(togglePassBtn.isSelected());
			togglePassBtn.setText(togglePassBtn.isSelected()?"SHOW":"HIDE");
			togglePassBtn.setOnAction(_->{
				gmailPass.setVisible(togglePassBtn.isSelected());
				yahooPass.setVisible(togglePassBtn.isSelected());
				orusPass.setVisible(togglePassBtn.isSelected());
				afsPass.setVisible(togglePassBtn.isSelected());
				gmailPassword.setVisible(!togglePassBtn.isSelected());
				yahooPassword.setVisible(!togglePassBtn.isSelected());
				orusPassword.setVisible(!togglePassBtn.isSelected());
				afsPassword.setVisible(!togglePassBtn.isSelected());
				togglePassBtn.setText(togglePassBtn.isSelected()?"SHOW":"HIDE");
			});
			vm = new TaxpayerViewModel();
			searchField.textProperty().addListener((_, _, newValue) -> filterTaxpayers(newValue));
			resetSelection();
			reloadTaxpayers();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	private void resetSelection() {
		isTaxpayerSelected = false;
		noSelectedHint.setVisible(true);
		editBtn.setDisable(true);
		delBtn.setDisable(true);
		slspBtn.setDisable(true);
		printBtn.setDisable(true);
		confirmationBtn.setDisable(true);
	}

	private void reloadTaxpayers() {
		try {
			List<Taxpayer> taxpayers = vm.loadTaxpayers();
			allTaxpayers = taxpayers != null ? taxpayers : new ArrayList<>();
			filterTaxpayers(searchField.getText());
			Taxpayer match = findByTin(allTaxpayers, Taxpayer.getTaxpayer());
			if (match != null) loadTaxpayer(match);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private Taxpayer findByTin(List<Taxpayer> list, Taxpayer target) {
		if (target == null || target.getTinNum() == null) return null;
		return list.stream()
				.filter(t -> target.getTinNum().equals(t.getTinNum()))
				.findFirst()
				.orElse(null);
	}
	
	private void renderTaxpayerCards(List<Taxpayer> taxpayers) {
		displayedTaxpayers = taxpayers;
		
		cards.clear();
		taxpayerContainer.getChildren().clear();
		Taxpayer current = isTaxpayerSelected ? findByTin(taxpayers, Taxpayer.getTaxpayer()) : null;
		selectedIndex = current == null ? -1 : taxpayers.indexOf(current);

		for (int i = 0; i < taxpayers.size(); i++) {
			Taxpayer taxpayer = taxpayers.get(i);
			TaxpayerCard card = new TaxpayerCard(taxpayer);
			card.setSelected(i == selectedIndex);
			card.addEventHandler(MouseEvent.MOUSE_CLICKED, _ -> loadTaxpayer(taxpayer));
			cards.add(card);
			taxpayerContainer.getChildren().add(card);
		}
	}
	@FXML
	public void SelectNext(ActionEvent e) {
		moveSelection(1);
	}
	@FXML
	public void SelectPrevious(ActionEvent e) {
		moveSelection(-1);
	}

	private void moveSelection(int delta) {
		if (displayedTaxpayers.isEmpty()) return;
		int next = selectedIndex < 0
				? 0
				: Math.max(0, Math.min(displayedTaxpayers.size() - 1, selectedIndex + delta));  
		if (next == selectedIndex) return;
		loadTaxpayer(displayedTaxpayers.get(next));
	}

	private void updateSelection(int index) {
		if (selectedIndex >= 0 && selectedIndex < cards.size()) {
			cards.get(selectedIndex).setSelected(false);
		}
		selectedIndex = index;
		if (index >= 0 && index < cards.size()) {
			TaxpayerCard card = cards.get(index);
			card.setSelected(true);
			scrollToCard(card);
		}
	}
	private void scrollToCard(TaxpayerCard card) {
		Parent p = taxpayerContainer.getParent();
		while (p != null && !(p instanceof ScrollPane)) p = p.getParent();
		if (!(p instanceof ScrollPane sp)) return;
		Platform.runLater(() -> {
			double contentH = taxpayerContainer.getBoundsInLocal().getHeight();
			double viewH = sp.getViewportBounds().getHeight();
			if (contentH <= viewH) return;
			double y = card.getBoundsInParent().getMinY();
			double h = card.getBoundsInParent().getHeight();
			double top = sp.getVvalue() * (contentH - viewH);
			if (y < top) sp.setVvalue(y / (contentH - viewH));
			else if (y + h > top + viewH) sp.setVvalue((y + h - viewH) / (contentH - viewH));
		});
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

		return containsIgnoreCase(taxpayer.getName().getFullName(3), query)
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
		this.isTaxpayerSelected = true;
		noSelectedHint.setVisible(!this.isTaxpayerSelected);
		editBtn.setDisable(!this.isTaxpayerSelected);
		delBtn.setDisable(!this.isTaxpayerSelected);
		slspBtn.setDisable(!this.isTaxpayerSelected);
		printBtn.setDisable(!this.isTaxpayerSelected);
		confirmationBtn.setDisable(!this.isTaxpayerSelected);
		PersonalInfo person = taxpayer.getPersonalInfo();
		Account acc = taxpayer.getAccount();
		tinNum.setText(taxpayer.getTinNum());
		fullName.setText(taxpayer.getName().getFullName(3));
		tradeName.setText(taxpayer.getTradeName());
		bussAddress.setText(taxpayer.getBussAddress());
		bussKind.setText(taxpayer.getBussKind());
		psic.setText(taxpayer.getPsic());
		String forms = taxpayer.getFormTypes() != null ? taxpayer.getFormTypes() : "";
		String vat = taxpayer.getVat();
		taxNformTypes.setText(vat == null || vat.isEmpty() ? forms : forms + " (" + vat + ")");
		String bd = person.getBirthdate();
		bday.setText(bd != null && !bd.isEmpty()
				? LocalDate.parse(bd).format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")) : "");
		bplace.setText(person.getBirthplace());
		civilStatus.setText(person.getCivilStatus());
		residence.setText(person.getResidence());
		tinSpouse.setText(person.getSpouseTin());
		spouseName.setText(person.getSpouseName());
		fatherName.setText(person.getFatherName());
		motherName.setText(person.getMotherMaidenName());
		cpNum.setText(person.getCpNum());
		gmailEmail.setText(!acc.getGmailEmail().isEmpty()?acc.getGmailEmail()+"@gmail.com":"");
		gmailPass.setText(acc.getGmailPass());
		yahooEmail.setText(!acc.getYahooEmail().isEmpty()?acc.getYahooEmail()+"@yahoo.com":"");
		yahooPass.setText(acc.getYahooPass());
		orusUser.setText(acc.getOrusName());
		orusPass.setText(acc.getOrusPass());
		afsUser.setText(acc.getAfsName());
		afsPass.setText(acc.getAfsPass());
		fbName.setText(acc.getFbName());
		recoveryEmail.setText(acc.getRecoveryEmail());
		
		gmailPassword.setText(acc.getGmailEmail());
		yahooPassword.setText(acc.getYahooPass());
		orusPassword.setText(acc.getOrusPass());
		afsPassword.setText(acc.getAfsPass());
		updateSelection(displayedTaxpayers.indexOf(taxpayer));
	}
	
	@FXML
	public void SLSP(ActionEvent e) {
		if(!this.isTaxpayerSelected) return;
		Pages.change(e,Pages.SLSP);
	}
	@FXML
	public void PrintTaxpayer(ActionEvent e) {
		if(!this.isTaxpayerSelected) return;
		Pages.change(e,Pages.TAXPAYER_PRINT);
	}
	@FXML
	public void BirConfirmation(ActionEvent e) {
		Pages.change(e, Pages.TAXPAYER_BIR_CONFIRMATION);
	}
	@FXML
	public void Logout(ActionEvent e) {
		Pages.change(e, Pages.AUTH);
		Taxpayer.setTaxpayer(null);
	}
	@FXML
	public void AddTaxpayer(ActionEvent e) {
		Pages.change(e,Pages.TAXPAYER_CREATE);
	}
	@FXML
	public void EditTaxpayer(ActionEvent e) {
		if(!this.isTaxpayerSelected) return;
		Pages.change(e,Pages.EDIT_TAXPAYER);
	}
	@FXML
	public void DeleteTaxpayer(ActionEvent e) {
		if(!this.isTaxpayerSelected) return;
		DeleteTaxpayer deleteView = new DeleteTaxpayer(Taxpayer.getTaxpayer().getId(), this::onTaxpayerDeleted);
		Pages.popupComponent(deleteView);
	}
	private void onTaxpayerDeleted() {
		Taxpayer.setTaxpayer(null);
		clearDetails();
		resetSelection();
		reloadTaxpayers();
	}
	private void clearDetails() {
		List.of(tinNum, fullName, tradeName, bussAddress, bussKind, taxNformTypes, psic,
				bday, bplace, civilStatus, residence, tinSpouse, spouseName, fatherName, motherName, cpNum,
				gmailEmail, gmailPass, yahooEmail, yahooPass, orusUser, orusPass, afsUser, afsPass,
				fbName, recoveryEmail,
				gmailPassword, yahooPassword, orusPassword, afsPassword)
			.forEach(TextField::clear);
	}
	
	@FXML private TextField tinNum;
	@FXML private TextField fullName;
	@FXML private TextField tradeName;
	@FXML private TextField bussAddress;
	@FXML private TextField bussKind;
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
	
	@FXML private PasswordField gmailPassword;
	@FXML private PasswordField yahooPassword;
	@FXML private PasswordField orusPassword;
	@FXML private PasswordField afsPassword;
	
	@FXML private ToggleButton togglePassBtn;
}