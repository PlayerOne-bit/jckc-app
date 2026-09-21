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
	private static final String DEFAULT_CARD_STYLE = """
			-fx-border-radius: 10;
			-fx-background-radius: 10;
			-fx-border-color: gray;
			""";
	private static final String SELECTED_CARD_STYLE = """
			-fx-background-color: #2563eb;
			-fx-border-radius: 10;
			-fx-background-radius: 10;
			-fx-border-color: gray;
			""";
	private static final String DEFAULT_TEXT_STYLE = """
			-fx-text-fill: black;
			""";
	private static final String SELECTED_TEXT_STYLE = """
			-fx-text-fill: white;
			""";
	
	
	public TaxpayerCard(Taxpayer taxpayer) {
		Pages.child(Pages.TAXPAYER_CARD, this, TaxpayerCard.class);
		tinNumber.setText(taxpayer.getTinNum());
		taxpayerName.setText(taxpayer.getName().getFullName(2));
		tradeName.setText(taxpayer.getTradeName());
		businessAddress.setText(taxpayer.getBussAddress());
	}
	public void setSelected(boolean selected) {
		setStyle(selected ? SELECTED_CARD_STYLE : DEFAULT_CARD_STYLE);
		String textStyle = selected ? SELECTED_TEXT_STYLE : DEFAULT_TEXT_STYLE;
		tinNumber.setStyle(textStyle);
		taxpayerName.setStyle(textStyle);
		tradeName.setStyle(textStyle);
		businessAddress.setStyle(textStyle);
	}
}
