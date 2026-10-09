package app.views;

import java.util.ArrayList;
import java.util.List;

import app.models.SLSP;
import app.models.Taxpayer;
import app.viewmodels.SlspViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public class SlspController {
    private static final String SELECTED_STYLE =
            "-fx-border-color: #2196f3; -fx-border-width: 2; -fx-background-color: #e8f4fd;";
    static String lastSelectedPeriod;
    @FXML private Label taxpayerName;
    @FXML private Label tinNumber;
    @FXML private Label tradeName;
    @FXML private BorderPane root;
    @FXML private TextField searchField;
    @FXML private VBox slspContainer;
    @FXML private FlowPane noSelectedHint;

    private SlspViewModel vm;

    // what is currently displayed, in the same order
    private final List<SLSP> shown = new ArrayList<>();
    private final List<SlspCard> cards = new ArrayList<>();
    private int selectedIndex = -1;

    @FXML
    private void initialize() throws Exception {
        Pages.bindShortcut(root, "Ctrl+N", this::AddSlsp);
        Pages.bindShortcut(root, "ESC", this::back);
        setupListNavigation();

        taxpayerName.setText(Taxpayer.getTaxpayer().getName().getFullName(1));
        tinNumber.setText(Taxpayer.getTaxpayer().getTinNum());
        tradeName.setText(Taxpayer.getTaxpayer().getTradeName());

        vm = new SlspViewModel();

        searchField.textProperty().addListener((_, _, _) -> renderSlsps());

        renderSlsps();
    }

    private void renderSlsps() {
        List<SLSP> all = vm.loadSLSPs();
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();

        slspContainer.getChildren().clear();
        shown.clear();
        cards.clear();

        List<SLSP> filtered = all.stream()
                .filter(s -> query.isEmpty()
                        || s.getPeriod().toLowerCase().contains(query)
                        || s.getTitle().toLowerCase().contains(query))
                .toList();

        for (SLSP slsp : filtered) {
        	SlspCard card = new SlspCard(slsp, e -> openForEdit(slsp, e));
            card.getProperties().put("baseStyle", card.getStyle());
            card.addEventFilter(MouseEvent.MOUSE_PRESSED, _ -> select(cards.indexOf(card)));
            slspContainer.getChildren().add(card);
            shown.add(slsp);
            cards.add(card);
        }

        noSelectedHint.setVisible(filtered.isEmpty());

        int initial = 0;
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).getPeriod().equals(lastSelectedPeriod)) {
                initial = i;
                break;
            }
        }
        selectedIndex = -1;
        select(shown.isEmpty() ? -1 : initial);
    }

    // ---------- Keyboard navigation: Up / Down move, Enter opens ----------

    private void setupListNavigation() {
        root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            KeyCode code = ev.getCode();
            if (code != KeyCode.UP && code != KeyCode.DOWN && code != KeyCode.ENTER) return;
            if (ev.isControlDown() || ev.isAltDown() || ev.isShiftDown()) return;

            // Let a focused button (Back, New) handle its own Enter
            Node focused = root.getScene() == null ? null : root.getScene().getFocusOwner();
            if (focused instanceof Button) return;

            if (code == KeyCode.ENTER) {
                if (selectedIndex >= 0 && selectedIndex < shown.size()) {
                    SlspCard card = cards.get(selectedIndex);
                    openForEdit(shown.get(selectedIndex), new ActionEvent(card, card));
                }
            } else if (!shown.isEmpty()) {
                int next = selectedIndex + (code == KeyCode.DOWN ? 1 : -1);
                select(Math.max(0, Math.min(next, shown.size() - 1)));
            }
            ev.consume();
        });
    }

    private void select(int index) {
        // clear the old highlight
        if (selectedIndex >= 0 && selectedIndex < cards.size()) {
            SlspCard old = cards.get(selectedIndex);
            old.setStyle((String) old.getProperties().get("baseStyle"));
        }
        selectedIndex = index;
        if (index < 0 || index >= cards.size()) return;

        lastSelectedPeriod = shown.get(index).getPeriod();   // remember it

        SlspCard card = cards.get(index);
        String base = (String) card.getProperties().get("baseStyle");
        card.setStyle((base == null ? "" : base) + SELECTED_STYLE);
        scrollIntoView(card);
    }

    private void scrollIntoView(Node card) {
        Node n = slspContainer.getParent();
        while (n != null && !(n instanceof ScrollPane)) n = n.getParent();
        if (!(n instanceof ScrollPane sp)) return;

        slspContainer.applyCss();
        slspContainer.layout();

        double contentH = slspContainer.getBoundsInLocal().getHeight();
        double viewH = sp.getViewportBounds().getHeight();
        if (contentH <= viewH) return;

        double cardY = card.getBoundsInParent().getMinY();
        double cardH = card.getBoundsInParent().getHeight();
        double top = sp.getVvalue() * (contentH - viewH);   // current top edge

        if (cardY < top) {
            sp.setVvalue(cardY / (contentH - viewH));
        } else if (cardY + cardH > top + viewH) {
            sp.setVvalue((cardY + cardH - viewH) / (contentH - viewH));
        }
    }

    private void openForEdit(SLSP slsp, ActionEvent e) {
        SlspManagerController.editingSlsp = slsp;
        Pages.change(e, Pages.SLSP_MANAGER);
    }

    @FXML
    public void back(ActionEvent e) {
        Pages.change(e, Pages.HOME);
    }

    @FXML
    public void AddSlsp(ActionEvent e) {
        SlspManagerController.editingSlsp = null;
        Pages.change(e, Pages.SLSP_MANAGER);
    }
}