package app.views;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import app.models.Purchase;
import app.models.Sale;
import app.models.Taxpayer;
import app.viewmodels.SlspViewModel;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class SlspManagerController {
	@FXML private ComboBox<Integer> years;
	@FXML private ComboBox<String> months;
	@FXML private BorderPane root;
	@FXML private Button delBtn;
	@FXML private TextField sumInput;
	@FXML private TextField totalSum;
	@FXML private VBox sumContainer;
	@FXML private TableView<Sale> sTable;
	@FXML private TableView<Purchase> pTable;
	@FXML private Text tradeNameText;
	
	private static List<BigDecimal> total = new ArrayList<>();
	
	@FXML
	private void initialize() {
		Pages.bindShortcut(root,"Ctrl+S",this::SaveSlsp);
		Pages.bindShortcut(root,"Ctrl+D",this::DeleteSlsp);
		Pages.bindShortcut(root,"ESC",this::back);
		Pages.bindShortcut(sumInput,"Enter",this::sumInput);
		Pages.bindShortcut(root,"Ctrl+E" , sumInput);
		tradeNameText.setText("SLSP : "+Taxpayer.getTaxpayer().getTradeName());
		years.setValue(LocalDate.now().getYear());
		years.setItems(FXCollections.observableArrayList(LocalDate.now().getYear(),LocalDate.now().getYear()-1));
		months.setItems(FXCollections.observableArrayList(
				"01 - January",
				"02 - February",
				"03 - March",
				"04 - April",
				"05 - May",
				"06 - June",
				"07 - July",
				"08 - August",
				"09 - September",
				"10 - October",
				"11 - November",
				"12 - December"
				));
		delBtn.setVisible(!SlspViewModel.isNew());
		setUpNumberTextField(sumInput);
	}
	private void setUpNumberTextField(TextField t) {
		t.textProperty().addListener((_,old,newVal)->{
			if(!newVal.matches("^-?\\d*(\\.\\d{0,2})?$")) t.setText(old);
		});
	}
	@FXML
	public void sumInput(ActionEvent e) {
		String input=sumInput.getText();
		if (input==null) return;
		BigDecimal inputAmount = new BigDecimal(input);
		total.add(inputAmount);
		BigDecimal sum =BigDecimal.ZERO;
		sumContainer.getChildren().clear();
		for(BigDecimal amount: total) {
			TextField newTextField = new TextField(""+amount);
			newTextField.setEditable(false);
			newTextField.setStyle("""
					-fx-background-color: none; 
					-fx-background-insets: 0; 
					-fx-background-radius: 0; 
					-fx-padding: 0;
					-fx-text-fill:black;
					-fx-alignment:center;
					""");
			newTextField.addEventHandler(MouseEvent.MOUSE_CLICKED,
					_->deleteTextField(amount,newTextField,e));
			sum=sum.add(amount);
			sumContainer.getChildren().add(newTextField);
		}
		totalSum.setText("Total: "+sum);
		sumInput.setText("");
		
	}
	private void deleteTextField(BigDecimal d,TextField t,ActionEvent e) {
		if(total.size()<=0)return;
		sumContainer.getChildren().remove(t);
		total.remove(d);
		BigDecimal sum = BigDecimal.ZERO;
		for(BigDecimal amount: total) sum=sum.add(amount);
		totalSum.setText("Total: "+sum);
	}
	@FXML
	public void clearSumInput() {
		sumInput.setText("");
		total.clear();
		sumContainer.getChildren().clear();
		totalSum.setText("Total: 0");
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
