package app.views;

import java.io.IOException;
import java.util.function.Consumer;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class Pages {
    public final static String
            AUTH = "fxml/auth.fxml",
            HOME = "fxml/taxpayer.fxml",
            ADD_TAXPAYER="fxml/add_taxpayer.fxml",
            TAXPAYER_CARD="fxml/taxpayer_card.fxml",
            EDIT_TAXPAYER = "fxml/edit_taxpayer.fxml",
            DELETE_TAXPAYER = "fxml/delete_taxpayer.fxml",
            SLSP="fxml/slsp.fxml",
            SLSP_CARD = "fxml/slsp_card.fxml",
            SLSP_MANAGER="fxml/slsp_manager.fxml",
            SLSP_PRINT = "fxml/slsp_print.fxml",
            SUPPLIER = "fxml/supplier.fxml",
            SUPPLIER_CARD = "fxml/supplier_card.fxml";
            
    
    private static Stage stage;
    private static Scene scene;
    private static Parent root;
    public static void bindShortcut(Node target, String shortcutStr, Consumer<ActionEvent> action) {
        KeyCombination combination = KeyCombination.valueOf(shortcutStr);
        target.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (combination.match(event)) {
                action.accept(new ActionEvent(target, event.getTarget()));
                event.consume();
            }
        });
    }
    public static void bindShortcut(Parent target, String shortcutStr, Consumer<ActionEvent> action) {
    	KeyCombination combination = KeyCombination.valueOf(shortcutStr);
        
        target.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (combination.match(event)) { 
                action.accept(new ActionEvent(target, event.getTarget()));
                event.consume();
            }
        });
    }
    public static void bindShortcut(Parent target, String shortcutStr, Node node) {
    	KeyCombination combination = KeyCombination.valueOf(shortcutStr);
        target.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (combination.match(event)) { 
                node.requestFocus();
                if(node instanceof TextInputControl) {
                	((TextInputControl) node).selectAll();
                }
                event.consume();
            }
        });
    }
    public static void child(String fxml, Object parent, Class<?> contextClass) {
        FXMLLoader fxmlLoader = new FXMLLoader(contextClass.getResource(fxml));
        fxmlLoader.setRoot(parent);
        fxmlLoader.setController(parent);
        try {
            fxmlLoader.load();
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void popupComponent(Parent componentRoot) {
        try {
            Stage stage2 = new Stage();
            stage2.getIcons().add(new Image(Pages.class.getResourceAsStream("/app/views/fxml/images/logo.png")));
            stage2.setScene(new Scene(componentRoot));
            stage2.initModality(Modality.APPLICATION_MODAL);
            stage2.setResizable(false);
            stage2.showAndWait();
        } catch(Exception d) {
            d.printStackTrace();
        }
    }
    

    public static void change(ActionEvent e, String next) {
        try {
            root = FXMLLoader.load(Pages.class.getResource(next));
            stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
            if (stage.getScene() != null) {
                scene = stage.getScene();
                scene.setRoot(root);
            } else {
                scene = new Scene(root);
                stage.setScene(scene);
            }
            
            stage.centerOnScreen();
            stage.show();
        } catch(Exception error) {
            error.printStackTrace();;
        }
    }
}
