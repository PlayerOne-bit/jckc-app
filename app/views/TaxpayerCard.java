package app.views;

import app.models.Taxpayer;
import javafx.fxml.FXML;
import javafx.scene.layout.FlowPane;
import javafx.scene.text.Text;

public class TaxpayerCard extends FlowPane{
	@FXML private Text tinNumber;
	@FXML private Text taxpayerName;
	@FXML private Text tradeName;
	@FXML private Text businessAddress;
	
	public TaxpayerCard(Taxpayer taxpayer) {
		Pages.child(Pages.TAXPAYER_CARD, this, TaxpayerCard.class);
		tinNumber.setText(taxpayer.getTinNum());
		taxpayerName.setText(taxpayer.getTaxName());
		tradeName.setText(taxpayer.getTradeName());
		businessAddress.setText(taxpayer.getBussAddress());
	}
}
