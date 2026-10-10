package app.views;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import app.models.Account;
import app.models.Name;
import app.models.PersonalInfo;
import app.models.Taxpayer;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

public class TaxpayerPrintController {

    private static final String MASK = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    @FXML private BorderPane root;
    @FXML private Text titleText, previewSubtitle;
    @FXML private VBox previewPage;
    @FXML private GridPane sectionTaxpayer, sectionPersonal, sectionAccounts;
    @FXML private CheckBox includeTaxpayer, includePersonal, includeAccounts, showPasswords;

    @FXML private Label pFullName, pTinNum, pTradeName, pBussAddress, pPsic, pBussKind, pTaxNformTypes;
    @FXML private Label pBday, pBplace, pResidence, pCivilStatus, pCpNum, pTinSpouse,
                        pSpouseName, pFatherName, pMotherName;
    @FXML private Label pGmailEmail, pGmailPass, pYahooEmail, pYahooPass, pOrusUser, pOrusPass,
                        pAfsUser, pAfsPass, pFbName, pRecoveryEmail;

    @FXML
    private void initialize() {
        Pages.bindShortcut(root, "ESC", this::back);
        Pages.bindShortcut(root, "Ctrl+P", this::printTaxpayer);

        bindSection(includeTaxpayer, sectionTaxpayer);
        bindSection(includePersonal, sectionPersonal);
        bindSection(includeAccounts, sectionAccounts);
        showPasswords.selectedProperty().addListener((_, _, _) -> fill());

        fill();
    }

    private void bindSection(CheckBox box, GridPane section) {
        section.visibleProperty().bind(box.selectedProperty());
        section.managedProperty().bind(box.selectedProperty());
    }

    // ---------- Fill the preview ----------

    private void fill() {
        Taxpayer t = Taxpayer.getTaxpayer();
        if (t == null) return;

        previewSubtitle.setText(nz(t.getTradeName()));

        // Taxpayer
        Name n = t.getName();
        pFullName.setText(n == null ? "" : nz(n.getFullName(2)));   // "Last, First M."
        pTinNum.setText(nz(t.getTinNum()));
        pTradeName.setText(nz(t.getTradeName()));
        pBussAddress.setText(nz(t.getBussAddress()));
        pPsic.setText(nz(t.getPsic()));
        pBussKind.setText(nz(t.getBussKind()));
        pTaxNformTypes.setText(taxAndForm(t));

        // Personal information
        PersonalInfo p = t.getPersonalInfo();
        if (p != null) {
            pBday.setText(formatDate(p.getBirthdate()));
            pBplace.setText(nz(p.getBirthplace()));
            pResidence.setText(nz(p.getResidence()));
            pCivilStatus.setText(nz(p.getCivilStatus()));
            pCpNum.setText(nz(p.getCpNum()));
            pTinSpouse.setText(nz(p.getSpouseTin()));
            pSpouseName.setText(nz(p.getSpouseName()));
            pFatherName.setText(nz(p.getFatherName()));
            pMotherName.setText(nz(p.getMotherMaidenName()));
        }

        // Accounts
        Account a = t.getAccount();
        if (a != null) {
            pGmailEmail.setText(nz(a.getGmailEmail()));
            pGmailPass.setText(pw(a.getGmailPass()));
            pYahooEmail.setText(nz(a.getYahooEmail()));
            pYahooPass.setText(pw(a.getYahooPass()));
            pOrusUser.setText(nz(a.getOrusName()));
            pOrusPass.setText(pw(a.getOrusPass()));
            pAfsUser.setText(nz(a.getAfsName()));
            pAfsPass.setText(pw(a.getAfsPass()));
            pFbName.setText(nz(a.getFbName()));
            pRecoveryEmail.setText(nz(a.getRecoveryEmail()));
        }
    }

    private String taxAndForm(Taxpayer t) {
        String forms = nz(t.getFormTypes());
        String vat = nz(t.getVat());
        if (forms.isBlank()) return vat;
        return vat.isBlank() ? forms : forms + " (" + vat + ")";
    }

    private String formatDate(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            return LocalDate.parse(raw).format(LONG_DATE);
        } catch (Exception e) {
            return raw;   // not ISO format, show as stored
        }
    }

    private String pw(String value) {
        if (value == null || value.isEmpty()) return "";
        return showPasswords.isSelected() ? value : MASK;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ---------- Actions ----------

    @FXML
    public void printTaxpayer(ActionEvent e) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) return;
        if (!job.showPrintDialog(root.getScene().getWindow())) return;

        PageLayout layout = job.getPrinter().createPageLayout(
                Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT);

        // Unscaled size of the paper (layout bounds exclude the shadow effect)
        double w = previewPage.getLayoutBounds().getWidth();
        double h = previewPage.getLayoutBounds().getHeight();

        double scale = Math.min(1.0, Math.min(
                layout.getPrintableWidth() / w,
                layout.getPrintableHeight() / h));

        // Center horizontally on the page; cancel the on-screen layout offset
        double offsetX = (layout.getPrintableWidth() - w * scale) / 2.0;
        Translate move = new Translate(
                offsetX - previewPage.getLayoutX(),
                -previewPage.getLayoutY());
        Scale shrink = new Scale(scale, scale, 0, 0);

        String oldStyle = previewPage.getStyle();
        previewPage.setStyle("-fx-background-color: white;");   // no drop shadow
        previewPage.getTransforms().addAll(move, shrink);
        try {
            if (job.printPage(layout, previewPage)) job.endJob();
        } finally {
            previewPage.getTransforms().removeAll(move, shrink);
            previewPage.setStyle(oldStyle);                      // restore the preview look
        }
    }

    @FXML
    public void back(ActionEvent e) {
        Pages.change(e, Pages.HOME);   // replace with your actual home constant
    }
}