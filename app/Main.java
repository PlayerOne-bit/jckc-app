package app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
	    try {
	        Parent root = FXMLLoader.load(getClass().getResource("/app/views/fxml/auth.fxml"));
	        Scene scene= new Scene(root);
	        String css=this.getClass().getResource("/app/views/fxml/application.css").toExternalForm();
	        scene.getStylesheets().add(css);
	        stage.setTitle("JCKC Accounting and Bookkeeping Services");
	        stage.getIcons().add(new Image(getClass().getResourceAsStream("/app/views/fxml/images/logo.png")));
	        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
	        stage.setMinWidth(screenBounds.getWidth());
	        stage.setMinHeight(screenBounds.getHeight());
	        System.out.printf("""
	        		width: %f
	        		height: %f
	        		""",screenBounds.getWidth(), screenBounds.getHeight());
	        stage.setScene(scene);
	        stage.setMaximized(true);
	        stage.show();
	    }catch(Exception e) {
	    	e.printStackTrace();
	    }
	}

    public static void main(String[] args) {
        launch(args);
    }
}
