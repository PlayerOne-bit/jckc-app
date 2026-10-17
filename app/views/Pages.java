package app.views;

import java.io.IOException;
import java.util.function.Consumer;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
public class Pages {
    public final static String
            AUTH = "fxml/auth.fxml",
            HOME = "fxml/taxpayer.fxml",
            TAXPAYER_PRINT = "fxml/taxpayer_print.fxml",
            TAXPAYER_CREATE="fxml/taxpayer_create.fxml",
            TAXPAYER_CARD="fxml/taxpayer_card.fxml",
            TAXPAYER_BIR_CONFIRMATION = "fxml/taxpayer_bir_confirmation.fxml",
            TAXPAYER_APP_PASSWORD = "fxml/taxpayer_app_password.fxml",
            EDIT_TAXPAYER = "fxml/edit_taxpayer.fxml",
            DELETE_TAXPAYER = "fxml/delete_taxpayer.fxml",
            SLSP="fxml/slsp.fxml",
            SLSP_CARD = "fxml/slsp_card.fxml",
            SLSP_MANAGER="fxml/slsp_manager.fxml",
            SLSP_PRINT = "fxml/slsp_print.fxml",
            SUPPLIER = "fxml/supplier.fxml",
            SUPPLIER_CARD = "fxml/supplier_card.fxml";
    private static Stage announcementStage;
    private static Stage stage;
    private static Scene scene;
    private static Parent root;
    private static boolean announcementShownOnce = false;
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
    public static void toggleAnnouncement() {
        if (announcementStage != null && announcementStage.isShowing()) {
            announcementStage.hide();
            return;
        }

        showAnnouncement();
    }

    public static void showAnnouncement() {

        // Reuse the existing popup if it has already been created.
        if (announcementStage != null) {
            positionAnnouncement();

            announcementStage.show();
            announcementStage.toFront();

            return;
        }

        announcementStage = new Stage();

        announcementStage.initStyle(StageStyle.UNDECORATED);
        announcementStage.setTitle("Release Announcements");
        announcementStage.setAlwaysOnTop(true);
        announcementStage.setResizable(false);

        // Header
        Label title = new Label("JCKC Accounting and Bookkeeping Services App");
        title.setWrapText(true);
        title.setMaxWidth(280);
        title.setStyle(
        	    "-fx-font-size: 16px;" +
        	    "-fx-font-weight: bold;" +
        	    "-fx-text-fill: white;"
        	);

        Label version = new Label("RELEASE 1.0.1");
        version.setStyle(
            "-fx-font-size: 11px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: white;"
        );

        Button closeButton = new Button("✕");
        closeButton.setTooltip(
            new javafx.scene.control.Tooltip("Close announcement")
        );

        closeButton.setStyle(
        	    "-fx-background-color: transparent;" +
        	    "-fx-text-fill: white;" +
        	    "-fx-font-size: 16px;" +
        	    "-fx-cursor: hand;"
        	);

        closeButton.setOnAction(_ -> {
            announcementStage.hide();
        });

        VBox headerText = new VBox(5, title, version);

        HBox header = new HBox(headerText, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(10);
        HBox.setHgrow(headerText, javafx.scene.layout.Priority.ALWAYS);

        header.setPadding(new Insets(18));
        header.setStyle(
        	    "-fx-background-color: #E75480;"
        	);

        // Announcement content
        VBox content = new VBox(15);

        content.setPadding(new Insets(18));
        content.setStyle("-fx-background-color: #ffffff;");
        Label newHeading = new Label("🆕 WHAT'S NEW IN RELEASE 1.0.1");
        newHeading.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #E75480;"
        );
        content.getChildren().add(newHeading);

        addAnnouncementSection(
            content,
            "🔍 SLSP Manager: Search, Undo and Redo",
            "Find rows faster with the new search field. Undo and redo " +
            "buttons let you step back and forward through your changes " +
            "without retyping."
        );

        addAnnouncementSection(
            content,
            "✍️ Automatic Cell Commit",
            "A value you typed in a row is now saved automatically when " +
            "you click away from the field, so entries are no longer " +
            "lost when the cell loses focus."
        );

        addAnnouncementSection(
            content,
            "🖨️ Better SLSP Print Preview",
            "A cleaner, page-accurate preview with toggles to hide or " +
            "show columns, plus a new Summary page for the quarter."
        );

        addAnnouncementSection(
            content,
            "📊 Summary in Excel Export",
            "Exported workbooks now include a Summary sheet with the " +
            "quarterly income, purchases and tax due."
        );

        Label oldHeading = new Label("RELEASE 1.0.0");
        oldHeading.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #64748b;"
        );
        content.getChildren().add(oldHeading);
        addAnnouncementSection(
            content,
            "📋 Taxpayer Information Management",
            "Maintain an organized list of taxpayer clients. " +
            "Search, view, edit, and manage client information " +
            "without repeatedly searching through separate records."
        );

        addAnnouncementSection(
            content,
            "📊 SLSP Generation",
            "Generate SLSP reports using saved taxpayer information. " +
            "Export to Excel and produce printable reports, including PDF. " +
            "Reduce the need to manually copy templates, reformat reports, " +
            "or repeatedly change document names."
        );

        addAnnouncementSection(
            content,
            "📧 BIR Confirmation Email Integration",
            "Retrieve BIR confirmation emails directly inside the application. " +
            "Review and print confirmation emails without repeatedly opening " +
            "each client's email account for verification and documentation."
        );

        addAnnouncementSection(
            content,
            "🧮 Built-in Sum Calculator",
            "Perform quick calculations inside the application. " +
            "Save time on everyday computations without switching " +
            "to another calculator."
        );

        addAnnouncementSection(
            content,
            "🏢 Supplier Management",
            "Maintain an organized supplier list and reuse saved supplier " +
            "information, including TIN details. Reduce repeated typing " +
            "and the need to look up the same supplier information."
        );

        Label footer = new Label(
            "More improvements and bug fixes will come in future releases.\n"
            + "Developed by: Wakai One"
        );

        footer.setWrapText(true);
        footer.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-text-fill: #64748b;"
        );

        footer.setPadding(new Insets(10, 0, 0, 0));

        content.getChildren().add(footer);

        // Scrollable content
        ScrollPane scrollPane = new ScrollPane(content);

        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(
            ScrollPane.ScrollBarPolicy.NEVER
        );
        scrollPane.setVbarPolicy(
            ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        scrollPane.setStyle(
            "-fx-background-color: white;" +
            "-fx-background: white;"
        );

        // Root layout
        BorderPane root = new BorderPane();

        root.setTop(header);
        root.setCenter(scrollPane);

        root.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #cbd5e1;" +
            "-fx-border-width: 1px;"
        );

        Scene scene = new Scene(root, 380, 470);

        announcementStage.setScene(scene);

        

        positionAnnouncement();

        announcementStage.show();
        announcementStage.toFront();
    }

    private static void addAnnouncementSection(
            VBox parent,
            String heading,
            String description) {

        Label headingLabel = new Label(heading);

        headingLabel.setWrapText(true);

        headingLabel.setStyle(
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #0f172a;"
        );

        Label descriptionLabel = new Label(description);

        descriptionLabel.setWrapText(true);

        descriptionLabel.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-text-fill: #475569;" +
            "-fx-line-spacing: 3px;"
        );

        VBox section = new VBox(6, headingLabel, descriptionLabel);

        section.setPadding(new Insets(0, 0, 12, 0));

        parent.getChildren().add(section);
    }

    private static void positionAnnouncement() {

        if (announcementStage == null) {
            return;
        }

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();

        double width = 380;
        double height = 470;
        double margin = 15;

        announcementStage.setWidth(width);
        announcementStage.setHeight(height);

        announcementStage.setX(
            bounds.getMaxX() - width - margin
        );

        announcementStage.setY(
            bounds.getMaxY() - height - margin
        );
    }
    public static void showAnnouncementOnce() {
        if (announcementShownOnce) return;
        announcementShownOnce = true;
        showAnnouncement();
    }

    public static void hideAnnouncement() {
        if (announcementStage != null) announcementStage.hide();
    }
}
