package app.views;

import app.models.Taxpayer;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class TaxpayerCard extends VBox{
	@FXML private Label tinNumber;
	@FXML private Label taxpayerName;
	@FXML private Label tradeName;
	@FXML private Label businessAddress;
	
	public TaxpayerCard(Taxpayer taxpayer) {
		Pages.child(Pages.TAXPAYER_CARD, this, TaxpayerCard.class);
		tinNumber.setText(taxpayer.getTinNum());
		taxpayerName.setText(taxpayer.getName().getFullName(2));
		tradeName.setText(taxpayer.getTradeName());
		businessAddress.setText(taxpayer.getBussAddress());
	}
}
