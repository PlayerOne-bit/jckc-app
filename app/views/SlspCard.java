package app.views;

import java.math.BigDecimal;
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
    @FXML private Text dateModified;
    @FXML private Text dateCreated;

    public SlspCard(SLSP slsp, Consumer<ActionEvent> onOpen) {
        Pages.child(Pages.SLSP_CARD, this, SlspCard.class);

        period.setText(slsp.getTitle());
        salesRow.setText("" + slsp.getSales().size());
        purchasesRow.setText("" + slsp.getPurchases().size());

        BigDecimal oVat = BigDecimal.ZERO;
        BigDecimal iVat = BigDecimal.ZERO;
        for (Sale sale : slsp.getSales()) {
            oVat = oVat.add(sale.getOutputVat());
        }
        for (Purchase purchase : slsp.getPurchases()) {
            iVat = iVat.add(purchase.getInputVat());
        }
        outputVat.setText("\u20B1 " + oVat);
        inputVat.setText("\u20B1 " + iVat);
        dateModified.setText("Date Modified: " + formatDateTime(slsp.getDateModified()));
        dateCreated.setText("Date Created: " + formatDateTime(slsp.getDateCreated()));

        setOnMouseClicked(e -> onOpen.accept(new ActionEvent(this, e.getTarget())));
        setStyle(getStyle() + "-fx-cursor: hand;");
    }

    /** "2026-10-08T14:30:00" -> "10/08/2026 - 02:30 PM" (falls back to the raw text if it can't be parsed). */
    private static String formatDateTime(String iso) {
        if (iso == null || iso.isBlank()) return "";
        try {
            return LocalDateTime.parse(iso).format(DISPLAY_FORMAT);
        } catch (Exception e) {
            return iso;
        }
    }
}