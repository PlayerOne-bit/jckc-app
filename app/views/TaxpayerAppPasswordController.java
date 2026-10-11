package app.views;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.kordamp.ikonli.javafx.FontIcon;

import app.models.Account;
import app.models.Taxpayer;
import app.viewmodels.AppPasswordViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

public class TaxpayerAppPasswordController {

    private static final String ICON_EDIT = "fas-pen";
    private static final String ICON_SAVE = "fas-save";

    private static final String GOOGLE_PAGE = "https://myaccount.google.com/apppasswords";
    private static final String YAHOO_PAGE = "https://login.yahoo.com/account/security";

    /** Google and Yahoo both issue 16-character app passwords (shown in groups of 4). */
    private static final String APP_PASSWORD_PATTERN = "[A-Za-z0-9]{16}";

    private AppPasswordViewModel vm;
    private Taxpayer taxpayer;
    private boolean hasGmail;
    private boolean editing;          // false = field locked, true = typing a new password
    private String email;

    @FXML private Button browserBtn;
    @FXML private Button manageBtn;
    @FXML private FontIcon appPasswordFontIcon;
    @FXML private TextField appPasswordTextField;
    @FXML private Text headerText;
    @FXML private Text errorText;

    @FXML
    private void initialize() throws Exception {
        vm = new AppPasswordViewModel();
        taxpayer = Taxpayer.getTaxpayer();
        
        // Same priority as the mail loader: Gmail first, then Yahoo
        Account account = taxpayer.getAccount();
        if (account != null && has(account.getGmailEmail())) {
            hasGmail = true;
            email = withDomain(account.getGmailEmail(), "@gmail.com");
            browserBtn.setText("Direct to Gmail App Password");
        } else if (account != null && has(account.getYahooEmail())) {
            email = withDomain(account.getYahooEmail(), "@yahoo.com");
            browserBtn.setText("Direct to Yahoo App Password");
        }

        appPasswordTextField.setOnAction(this::ManageAppPassword);   // Enter saves

        if (email == null) {
            headerText.setText("Manage App Password");
            errorText.setText("Save a Gmail or Yahoo account for this taxpayer first.");
            browserBtn.setDisable(true);
            manageBtn.setDisable(true);
            appPasswordTextField.setDisable(true);
            return;
        }

        headerText.setText("Manage App Password of " + email);
        appPasswordTextField.setText(taxpayer.getAppPassword());   // null shows as empty
        errorText.setText("");
        setEditing(false);
    }

    // ---------- Open the provider's app-password page ----------

    @FXML
    public void DirectToAppPassword(ActionEvent e) {
        if (email == null) return;

        final String url;
        if (hasGmail) {
            // Google's account chooser pre-selects this email, then continues to the app-passwords page
            url = "https://accounts.google.com/AccountChooser?Email="
                    + URLEncoder.encode(email, StandardCharsets.UTF_8)
                    + "&continue=" + URLEncoder.encode(GOOGLE_PAGE, StandardCharsets.UTF_8);
        } else{
            url = YAHOO_PAGE;   // Yahoo: sign in as the email shown in the header, then "Create app password"
        }

        // Off the JavaFX thread so a slow browser launch can't freeze the window
        Thread t = new Thread(() -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new UnsupportedOperationException("No default browser available.");
                }
                Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception ex) {
                ex.printStackTrace();
                javafx.application.Platform.runLater(() -> errorText.setText(
                        "Could not open the browser. Go to " + (hasGmail ? GOOGLE_PAGE : YAHOO_PAGE)));
            }
        }, "open-browser");
        t.setDaemon(true);
        t.start();
    }

    // ---------- Edit / save ----------

    /** First click unlocks the field; the next click validates and saves it. */
    @FXML
    public void ManageAppPassword(ActionEvent e) {
        if (email == null) return;

        if (!editing) {
            errorText.setText("");
            setEditing(true);
            return;
        }

        // Google shows the password as "abcd efgh ijkl mnop"; store it without the spaces
        String entered = appPasswordTextField.getText() == null
                ? "" : appPasswordTextField.getText().replaceAll("\\s+", "");

        if (!entered.matches(APP_PASSWORD_PATTERN)) {
            errorText.setText("Invalid app password! It should be 16 characters, like abcd efgh ijkl mnop.");
            return;   // stay in edit mode so the user can fix it
        }

        try {
            if (vm.setAppPassword(taxpayer.getId(), entered)) {
                appPasswordTextField.setText(entered);
                errorText.setText("");
                setEditing(false);
            } else {
                errorText.setText("Could not save: this taxpayer no longer exists.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            errorText.setText("Could not save: " + ex.getMessage());
        }
    }

    private void setEditing(boolean value) {
        editing = value;
        appPasswordTextField.setDisable(!editing);

        // Green "save" look while editing, default look while locked
        manageBtn.setId(editing ? "green-button" : null);
        manageBtn.setText(editing?"SAVE":"EDIT");
        if (appPasswordFontIcon != null) {
            appPasswordFontIcon.setIconLiteral(editing ? ICON_SAVE : ICON_EDIT);
            appPasswordFontIcon.setIconColor(editing ? Color.WHITE : Color.BLACK);
        }

        if (editing) {
            appPasswordTextField.requestFocus();
            appPasswordTextField.selectAll();
        }
    }

    // ---------- Navigation ----------

    @FXML
    public void back(ActionEvent e) {
        if (email != null && has(taxpayer.getAppPassword())) {
            Pages.change(e, Pages.TAXPAYER_BIR_CONFIRMATION);
            return;
        }
        Pages.change(e, Pages.HOME);
    }

    // ---------- Helpers ----------

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    /** Stored value may be just the username; add the domain only when it's missing. */
    private static String withDomain(String stored, String domain) {
        String s = stored.trim();
        return s.contains("@") ? s : s + domain;
    }
}