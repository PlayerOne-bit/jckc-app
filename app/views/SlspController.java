package app.views;

import java.util.List;

import app.models.SLSP;
import app.models.Taxpayer;
import app.viewmodels.SlspViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public class SlspController {
    @FXML private Label taxpayerName;
    @FXML private Label tinNumber;
    @FXML private Label tradeName;
    @FXML private BorderPane root;
    @FXML private TextField searchField;
    @FXML private VBox slspContainer;
    @FXML private FlowPane noSelectedHint;

    private SlspViewModel vm;

    @FXML
    private void initialize() throws Exception {
        Pages.bindShortcut(root, "Ctrl+N", this::AddSlsp);
        Pages.bindShortcut(root, "ESC", this::back);

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

        List<SLSP> filtered = all.stream()
                .filter(s -> query.isEmpty()
                        || s.getPeriod().toLowerCase().contains(query)
                        || s.getTitle().toLowerCase().contains(query))
                .toList();

        for (SLSP slsp : filtered) {
            SlspCard card = new SlspCard(slsp, e -> openForEdit(slsp, e));
            slspContainer.getChildren().add(card);
        }

        noSelectedHint.setVisible(filtered.isEmpty());
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