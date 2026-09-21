package app.views;

import java.math.BigDecimal;

import app.models.Purchase;
import app.models.SLSP;
import app.models.Sale;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class SlspCard extends VBox{
	@FXML private Text period;
	@FXML private Text salesRow;
	@FXML private Text purchasesRow;
	@FXML private Text outputVat;
	@FXML private Text inputVat;
	@FXML private Text dateModified;
	@FXML private Text dateCreated;
	public SlspCard(SLSP slsp) {
		Pages.child(Pages.SLSP_CARD, this, SlspCard.class);
		period.setText(slsp.getPeriod());
		salesRow.setText(""+slsp.getSales().size());
		purchasesRow.setText(""+slsp.getPurchases().size());
		BigDecimal oVat=BigDecimal.ZERO;
		BigDecimal iVat=BigDecimal.ZERO;
		for(Sale sale : slsp.getSales()) {
			oVat.add(sale.getOutputVat());
		}
		for(Purchase purchase : slsp.getPurchases()) {
			iVat.add(purchase.getInputVat());
		}
		outputVat.setText("₱ "+oVat);
		inputVat.setText("₱ "+iVat);
		dateModified.setText("Date Modified: "+slsp.getDateModified());
		dateCreated.setText("Date Created: "+slsp.getDateCreated());
	}
}
