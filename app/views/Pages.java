package app.views;

import java.io.IOException;
import java.util.Stack;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class Pages {
    public final static String
            AUTH = "fxml/auth.fxml",
            HOME = "fxml/taxpayer.fxml",
            ADD_TAXPAYER="fxml/add_taxpayer.fxml",
            TAXPAYER_CARD="fxml/taxpayer_card.fxml",
            EDIT_TAXPAYER = "fxml/edit_taxpayer.fxml",
            DELETE_TAXPAYER = "fxml/delete_taxpayer.fxml";
    
    private static Stack<String> stack = new Stack<>(); 
    private static Stage stage, stage2;
    private static Scene scene;
    private static Parent root;

    public static void clear() {
        stack.clear();
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

    
    public static void cancel(ActionEvent e) {
        if (stage2 != null) {
            stage2.close();
        }
    }

    public static void main_menu(ActionEvent e) {
        cancel(e);
        try {
            root = FXMLLoader.load(Pages.class.getResource(AUTH));
            if (scene != null) {
                scene.setRoot(root);
            } else {
                scene = new Scene(root);
                stage.setScene(scene);
            }
            stage.centerOnScreen();
        } catch (IOException e1) {
            System.out.println(e1);
        }
    }
    
    public static void change(ActionEvent e, String prev, String next){
        Pages.stack.push(prev);
        change(e, next);
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
            System.out.println(error);
        }
    }
    
    public static void back(ActionEvent e) {
        try {
            if (!stack.isEmpty()) {
                String pages = stack.pop();
                root = FXMLLoader.load(Pages.class.getResource(pages));
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
            }
        } catch(Exception d) {
            System.out.println(d);
        }
    }
}
