package app.views;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import app.models.BirMail;
import app.models.Taxpayer;
import app.viewmodels.BirMailSyncManager;
import app.viewmodels.BirMailSyncManager.Job;
import app.viewmodels.BirMailSyncManager.Outcome;
import app.viewmodels.BirMailViewModel;
import app.viewmodels.BirMailViewModel.Credentials;
import app.viewmodels.BirMailViewModel.Provider;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.print.PrinterJob;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class TaxpayerBirConfirmationController {

    private static final DateTimeFormatter CARD_FMT =
            DateTimeFormatter.ofPattern("MM/dd/yyyy - hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter GMAIL_FMT =
            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a", Locale.ENGLISH);

    private static final String CARD_STYLE =
            "-fx-background-color: white; -fx-border-color: #c8c8c8; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;";
    private static final String CARD_SELECTED_STYLE =
            "-fx-background-color: #e8f0fe; -fx-border-color: #1a73e8; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;";
    private static final String GMAIL_LOGO =
            "<svg xmlns='http://www.w3.org/2000/svg' width='34' height='33' viewBox='0 0 194.713 192.05'>"
          + "<path fill='#4285f4' d='M58.182 192.05V93.14L27.507 65.077 0 49.504v125.091c0 9.658 7.825 17.455 17.455 17.455z'/>"
          + "<path fill='#34a853' d='M136.531 192.05h40.727c9.659 0 17.455-7.826 17.455-17.455V49.504l-31.156 17.837-27.026 25.798z'/>"
          + "<path fill='#ea4335' d='M58.182 93.14l-4.174-38.647 4.174-36.989L96 45.827l37.818-28.323 4.623 35.152-4.623 40.484L96 121.463z'/>"
          + "<path fill='#fbbc04' d='M136.531 17.504V93.14L194.713 49.504V26.23c0-21.585-24.64-33.89-41.89-20.945z'/>"
          + "<path fill='#c5221f' d='M0 49.504l26.759 20.07L58.182 93.14V17.504L41.89 5.285C24.61-7.66 0 4.646 0 26.23z'/>"
          + "</svg>";

    @FXML private BorderPane root;
    @FXML private Text headerText, subjectText, fromText, dateText, attachmentText;
    @FXML private TextField statusText;
    @FXML private VBox cardList;
    @FXML private Button refreshBtn, printBtn;
    @FXML private ProgressBar loadingBar;
    @FXML private WebView previewView;

    private BirMailViewModel vm;
    private String accountEmail = "";
    private Provider activeProvider = Provider.GMAIL;
    private VBox selectedCard;
    private BirMail selectedItem;

    // Background download this page is watching
    private boolean closed = false;                 // true once the user leaves this page
    private Job watchedJob;
    private ChangeListener<String> messageListener;

    @FXML
    private void initialize() throws Exception {
        Pages.bindShortcut(root, "ESC", this::back);
        Pages.bindShortcut(root, "Ctrl+P", this::PrintConfirmation);

        vm = new BirMailViewModel();

        Credentials cred = vm.getCredentials();
        headerText.setText(cred == null
                ? "BIR TAX CONFIRMATION"
                : "BIR TAX CONFIRMATION: " + cred.email() + " (" + cred.provider().label + ")");

        previewView.getEngine().setJavaScriptEnabled(false);   // never run scripts from email HTML
        printBtn.setDisable(true);
        loadMessages();
    }

    @FXML
    public void refresh(ActionEvent e) throws Exception {
        loadMessages();
    }

    @FXML
    public void back(ActionEvent e) {
        leave();
        Pages.change(e, Pages.HOME);
    }

    @FXML
    public void ManageAppPassword(ActionEvent e) {
        leave();
        Pages.change(e, Pages.TAXPAYER_APP_PASSWORD);
    }

    /** Stops this page from listening. The download itself keeps running and saves to the cache. */
    private void leave() {
        closed = true;
        detachFromJob();
    }

    private void detachFromJob() {
        loadingBar.progressProperty().unbind();
        loadingBar.visibleProperty().unbind();
        loadingBar.managedProperty().unbind();
        if (watchedJob != null && messageListener != null) {
            watchedJob.messageProperty().removeListener(messageListener);
        }
        watchedJob = null;
        messageListener = null;
    }

    // ---------- Loading ----------

    private void loadMessages() throws Exception {
        Credentials cred = vm.getCredentials();
        if (cred == null) {
            // either no Gmail/Yahoo email, or no app password saved yet
            statusText.setText(vm.getSetupProblem());
            return;
        }

        final Provider provider = cred.provider();
        accountEmail = cred.email();
        activeProvider = provider;

        // 1. Show what's already saved, instantly
        showCards(vm.loadSaved());

        // 2. Start a download, or join the one that is already running (even from an earlier visit)
        boolean joining = BirMailSyncManager.isRunning(vm.getTaxId());
        Job job = BirMailSyncManager.startOrJoin(vm);

        refreshBtn.setDisable(true);
        String current = job.messageProperty().get();
        statusText.setText(joining && current != null && !current.isBlank()
                ? current
                : "Checking " + provider.label + " for new messages...");

        detachFromJob();   // in case Refresh was pressed while an older job was still attached
        watchedJob = job;
        loadingBar.progressProperty().bind(job.progressProperty());   // indeterminate until the first message
        loadingBar.visibleProperty().bind(job.runningProperty());
        loadingBar.managedProperty().bind(loadingBar.visibleProperty());
        messageListener = (_, _, m) -> {
            if (m != null && !m.isBlank()) statusText.setText(m);
        };
        job.messageProperty().addListener(messageListener);

        job.whenDone(outcome -> onSyncDone(job, outcome, provider));
    }

    private void onSyncDone(Job job, Outcome outcome, Provider provider) {
        if (closed || root.getScene() == null) return;   // page was left; the cache is already updated
        if (watchedJob != job) return;                   // a newer job replaced this one
        detachFromJob();
        loadingBar.setVisible(false);
        loadingBar.setManaged(false);
        refreshBtn.setDisable(false);

        if (outcome.error() != null) {
            boolean haveSaved = !cardList.getChildren().isEmpty();
            statusText.setText((haveSaved ? "Showing saved messages. " : "")
                    + "Could not reach " + provider.label + ": " + outcome.error().getMessage());
            return;
        }

        List<BirMail> all = outcome.all();
        if (outcome.newCount() > 0 || outcome.reset()) showCards(all);
        statusText.setText(all.isEmpty()
                ? "No messages from " + BirMailViewModel.BIR_SENDER + " in " + accountEmail
                : all.size() + " message(s)"
                  + (outcome.newCount() == 0 ? "" : ", " + outcome.newCount() + " new"));
    }

    // ---------- Cards + preview ----------

    private void showCards(List<BirMail> items) {
        long keepUid = selectedItem == null ? -1 : selectedItem.uid();
        cardList.getChildren().clear();
        selectedCard = null;
        selectedItem = null;
        printBtn.setDisable(true);

        for (BirMail item : items) {
            VBox card = buildCard(item);
            cardList.getChildren().add(card);
            if (item.uid() == keepUid) select(card, item);   // keep the same message selected
        }
    }

    private VBox buildCard(BirMail item) {
        Text label = new Text(item.received().format(CARD_FMT) + ": " + item.subject());
        label.setWrappingWidth(320);

        VBox card = new VBox(label);
        card.setStyle(CARD_STYLE);
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(_ -> select(card, item));
        return card;
    }

    private void select(VBox card, BirMail item) {
        if (selectedCard != null) selectedCard.setStyle(CARD_STYLE);
        selectedCard = card;
        selectedItem = item;
        card.setStyle(CARD_SELECTED_STYLE);
        showPreview(item);
    }

    private void showPreview(BirMail item) {
        printBtn.setDisable(false);
        subjectText.setText(item.subject());
        fromText.setText(item.from());
        dateText.setText(item.received().format(CARD_FMT));
        attachmentText.setText(item.attachments().isEmpty()
                ? "" : "Attachment: " + String.join(", ", item.attachments()));
        String html = item.html() == null ? "<p>(empty message)</p>" : item.html();
        Platform.runLater(() -> previewView.getEngine().loadContent(html));
    }

    // ---------- Printing ----------

    @FXML
    public void PrintConfirmation(ActionEvent e) {
        if (selectedItem == null) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Select a message to print first.");
            a.setHeaderText(null);
            a.showAndWait();
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) return;
        if (!job.showPrintDialog(root.getScene().getWindow())) return;

        // Print from a separate WebView so the on-screen preview isn't disturbed
        WebView printView = new WebView();
        WebEngine engine = printView.getEngine();
        engine.setJavaScriptEnabled(false);
        engine.getLoadWorker().stateProperty().addListener((_, _, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                engine.print(job);
                job.endJob();
            } else if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) {
                job.cancelJob();
            }
        });
        engine.loadContent(buildPrintHtml(selectedItem));
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String buildPrintHtml(BirMail item) {
        String body = item.html() == null ? "<p>(empty message)</p>" : item.html();

        // Account owner: "Name <email@example.com>"
        String owner = Taxpayer.getTaxpayer().getName().getFullName(0);
        String ownerHtml = "<b>" + escape(owner) + " &lt;" + escape(accountEmail) + "&gt;</b>";

        // Sender: "BIR <ebirforms-noreply@bir.gov.ph>" -> bold name + address
        String from = item.from();
        int lt = from.indexOf('<');
        String fromHtml = lt > 0
                ? "<b>" + escape(from.substring(0, lt).trim()) + "</b> " + escape(from.substring(lt))
                : "<b>" + escape(from) + "</b>";

        String replyTo = item.replyTo().isBlank() ? "" : "Reply-To: " + escape(item.replyTo()) + "<br>";
        String to = item.to().isBlank() ? "" : "To: " + escape(item.to()) + "<br>";
        String attachment = item.attachments().isEmpty() ? ""
                : "<div style='margin-top:6px'>Attachment: "
                  + escape(String.join(", ", item.attachments())) + "</div>";

        // Logo + name of the mail provider the message was read from
        String brandHtml = activeProvider == Provider.GMAIL
                ? GMAIL_LOGO + " <span style='font-size:24px;color:#5f6368;vertical-align:middle'>Gmail</span>"
                : "<span style='font-size:26px;font-weight:bold;color:#6001d2;vertical-align:middle'>yahoo!</span>"
                  + " <span style='font-size:22px;color:#5f6368;vertical-align:middle'>mail</span>";

        return "<div style='font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#000'>"
                // top bar: provider logo + account owner
                + "<table style='width:100%;border-collapse:collapse'><tr>"
                + "<td style='vertical-align:middle'>" + brandHtml + "</td>"
                + "<td style='text-align:right;vertical-align:middle'>" + ownerHtml + "</td>"
                + "</tr></table>"
                // subject
                + "<div style='border-top:2px solid #999;margin-top:8px;padding-top:6px;font-size:16px'><b>"
                + escape(item.subject()) + "</b></div>"
                + "<div style='border-bottom:1px solid #999;padding-bottom:4px;color:#333'>1 message</div>"
                // sender block
                + "<table style='width:100%;border-collapse:collapse;margin-top:8px'><tr>"
                + "<td style='vertical-align:top'>" + fromHtml + "<br>" + replyTo + to + "</td>"
                + "<td style='vertical-align:top;text-align:right;white-space:nowrap'>"
                + item.received().format(GMAIL_FMT) + "</td>"
                + "</tr></table>"
                + attachment
                + "</div>"
                + body;
    }

    // ---------- Cache ----------

    @FXML
    public void ClearCache(ActionEvent e) {
        Credentials cred = vm.getCredentials();
        if (cred == null) {
            statusText.setText(vm.getSetupProblem());
            return;
        }
        if (refreshBtn.isDisabled() || BirMailSyncManager.isRunning(vm.getTaxId())) {   // a download is still running
            statusText.setText("Wait for the current download to finish, then try again.");
            return;
        }

        ButtonType clear = new ButtonType("Clear and reload", ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "This deletes the saved copies of these messages from this computer and downloads them again from "
                + cred.provider().label + ". It needs an internet connection.\n\n"
                + "Your mailbox itself is not touched.",
                clear, ButtonType.CANCEL);
        alert.setTitle("Clear saved messages");
        alert.setHeaderText("Clear the message cache?");
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/app/views/fxml/images/logo.png")));
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != clear) return;

        try {
            vm.clearSaved();
            showCards(List.of());   // empties the list and resets the selection
            clearPreview();
            loadMessages();         // fresh download of everything
        } catch (Exception ex) {
            ex.printStackTrace();
            statusText.setText("Could not clear the saved messages: " + ex.getMessage());
        }
    }

    private void clearPreview() {
        subjectText.setText("");
        fromText.setText("");
        dateText.setText("");
        attachmentText.setText("");
        previewView.getEngine().loadContent("");
    }
}