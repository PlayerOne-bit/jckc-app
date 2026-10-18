package app.views;

import java.math.BigDecimal;

import app.models.SLSP;
import app.models.Supplier;
import app.models.Taxpayer;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;

public class SlspImportController {
	@FXML private TextField searchField;
	@FXML private Text tradeNameText;
	@FXML private ComboBox<String> months;
	@FXML private BorderPane root;
	@FXML private Tab saleTab;
	@FXML private TableView<ImportSaleRow> sTable;
	@FXML private TableColumn<ImportSaleRow, Integer> sColNum;
    @FXML private TableColumn<ImportSaleRow, Integer> sColDate;
    @FXML private TableColumn<ImportSaleRow, String> sColInvoice;
    @FXML private TableColumn<ImportSaleRow, BigDecimal> sColExempt;
    @FXML private TableColumn<ImportSaleRow, BigDecimal> sColZeroRated;
    @FXML private TableColumn<ImportSaleRow, BigDecimal> sColTaxable;
    @FXML private TableColumn<ImportSaleRow, BigDecimal> sColOutputVat;
    @FXML private TableColumn<ImportSaleRow, BigDecimal> sColGross;
    @FXML private Tab purchaseTab;
    @FXML private TableView<ImportPurchaseRow> pTable;
    @FXML private TableColumn<ImportPurchaseRow, Integer> pColNum;
    @FXML private TableColumn<ImportPurchaseRow, Integer> pColDate;
    @FXML private TableColumn<ImportPurchaseRow, Supplier> pColSupplier;
    @FXML private TableColumn<ImportPurchaseRow, String> pColInvoice;
    @FXML private TableColumn<ImportPurchaseRow, BigDecimal> pColExempt;
    @FXML private TableColumn<ImportPurchaseRow, BigDecimal> pColZeroRated;
    @FXML private TableColumn<ImportPurchaseRow, BigDecimal> pColTaxable;
    @FXML private TableColumn<ImportPurchaseRow, BigDecimal> pColInputVat;
    @FXML private TableColumn<ImportPurchaseRow, BigDecimal> pColGross;
    @FXML private TableColumn<ImportPurchaseRow, BigDecimal> pColTotalAmt;
    @FXML private Tab supplierTab;
	@FXML private TableView<ImportSupplierRow> supplierTable;
	@FXML private TableColumn<ImportSupplierRow, String> supplierNum;
	@FXML private TableColumn<ImportSupplierRow, String> tNum;
	@FXML private TableColumn<ImportSupplierRow, String> bAddress;
	
	private SLSP slsp;
	private Taxpayer taxpayer;
	@FXML
	private void initialize() {
		taxpayer=Taxpayer.getTaxpayer();
		tradeNameText.setText("Import Excel to Application: "+taxpayer.getTradeName());
		Pages.bindShortcut(root, "Ctrl+F", searchField);
		Pages.bindShortcut(root, "Ctrl+S", this::SaveSlsp);
		Pages.bindShortcut(root, "Ctrl+W", this::ClearSlsp);
		Pages.bindShortcut(root, "Ctrl+I", this::ImportSlsp);
		Pages.bindShortcut(root, "Esc", this::back);
	}
	@FXML
	public void ClearSlsp(ActionEvent e) {
		//TODO: import excel to app
	}
	@FXML
	public void ImportSlsp(ActionEvent e) {
		//TODO: import excel to app
	}
	@FXML
	public void SaveSlsp(ActionEvent e) {
		//TODO: commits&saveEverything
		Pages.change(e,Pages.SLSP);
	}
	@FXML
	public void back(ActionEvent e) {
		Pages.change(e,Pages.SLSP);
	}
}
class ImportSaleRow{
	
}
class ImportPurchaseRow{
	
}
class ImportSupplierRow{
	
}
