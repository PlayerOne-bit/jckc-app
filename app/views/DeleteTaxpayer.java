package app.views;

import app.viewmodels.TaxpayerViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class DeleteTaxpayer extends VBox {
    private TaxpayerViewModel vm;
    private int id;
    private final Runnable onDeleted;
    
    @FXML private TextField textField;
    @FXML private Button delButton;

    public DeleteTaxpayer(int id,Runnable onDeleted) {
    	this.onDeleted=onDeleted;
        Pages.child(Pages.DELETE_TAXPAYER,this,DeleteTaxpayer.class);
        Pages.bindShortcut(this,"Enter",this::delete);
        this.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (this.getScene() != null && this.getScene().getWindow() != null) {
                    closeWindow();
                    event.consume();
                }
            }
        });
        this.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                closeWindow();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                // BUG FIX: Only close if the button is NOT disabled, or let 'delete' handle it.
                if (!delButton.isDisable()) {
                    delete(null); 
                }
                event.consume();
            }
        });
        try {
            this.vm = new TaxpayerViewModel();
            this.id = id;
            delButton.setDisable(true);
            textField.textProperty().addListener((_, _, newValue) -> {
                delButton.setDisable(!newValue.equalsIgnoreCase("DELETE"));
            });
            
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    @FXML
    public void delete(ActionEvent e) {
        if (delButton.isDisable()) return;
        try {
        	vm.deleteTaxpayer(id);
        	closeWindow();
        	if (onDeleted != null) {
        	    onDeleted.run();
        	}

        } catch (Exception ex) {
            ex.printStackTrace();

            javafx.scene.control.Alert alert =
                    new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.ERROR);

            alert.setTitle("Delete Failed");
            alert.setHeaderText("Unable to delete taxpayer");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }

    private void closeWindow() {
        if (getScene() != null && getScene().getWindow() != null) {
            Stage stage = (Stage) getScene().getWindow();
            stage.close();
        }
    }
}
