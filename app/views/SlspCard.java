package app.views;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

import app.models.Purchase;
import app.models.SLSP;
import app.models.Sale;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class SlspCard extends VBox {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd/yyyy - hh:mm a", Locale.ENGLISH);

    @FXML private Text period;
    @FXML private Text salesRow;
    @FXML private Text purchasesRow;
    @FXML private Text outputVat;
    @FXML private Text inputVat;
    @FXML private Text taxDue;
    @FXML private Text dateModified;
    @FXML private Text dateCreated;

    private static String formatPeso(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₱ " + formatter.format(
                amount.setScale(2, RoundingMode.HALF_UP)
        );
    }

    public SlspCard(SLSP slsp, Consumer<ActionEvent> onOpen) {
        Pages.child(Pages.SLSP_CARD, this, SlspCard.class);

        period.setText(slsp.getTitle());
        salesRow.setText(String.valueOf(slsp.getSales().size()));
        purchasesRow.setText(String.valueOf(slsp.getPurchases().size()));

        BigDecimal oVat = BigDecimal.ZERO;
        BigDecimal iVat = BigDecimal.ZERO;

        for (Sale sale : slsp.getSales()) {
            oVat = oVat.add(sale.getOutputVat());
        }

        for (Purchase purchase : slsp.getPurchases()) {
            iVat = iVat.add(purchase.getInputVat());
        }

        // Format VAT amounts as Philippine pesos.
        outputVat.setText(formatPeso(oVat));
        inputVat.setText(formatPeso(iVat));

        // Tax Due = Output VAT - Input VAT.
        BigDecimal taxDueAmount = oVat.subtract(iVat);
        taxDue.setText(formatPeso(taxDueAmount));

        dateModified.setText("Date Modified: " + formatDateTime(slsp.getDateModified()));
        dateCreated.setText("Date Created: " + formatDateTime(slsp.getDateCreated()));

        setOnMouseClicked(e ->
                onOpen.accept(new ActionEvent(this, e.getTarget()))
        );

        setStyle(getStyle() + "-fx-cursor: hand;");
    }

    private static String formatDateTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return "";
        }

        try {
            return LocalDateTime.parse(iso).format(DISPLAY_FORMAT);
        } catch (Exception e) {
            return iso;
        }
    }
}