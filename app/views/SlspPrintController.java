package app.views;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import app.database.SlspRepository;
import app.export.SlspExcelExporter;
import app.models.Name;
import app.models.Purchase;
import app.models.SLSP;
import app.models.Sale;
import app.models.Supplier;
import app.models.Taxpayer;
import app.viewmodels.SupplierViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class SlspPrintController {

    // ---------------------------------------------------------
    // CONSTANTS / FIELDS
    // ---------------------------------------------------------

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private static final BigDecimal VAT_DIVISOR = new BigDecimal("1.12");
    private static final BigDecimal VAT_RATE = new BigDecimal("0.12");

    private static final double PAGE_WIDTH = 1000;
    private static final double PAGE_HEIGHT = PAGE_WIDTH * 210.0 / 297.0;   // A4 landscape ratio
    private static final double PAGE_PADDING = 25;
    private static final double CONTENT_WIDTH = PAGE_WIDTH - 2 * PAGE_PADDING;
    private static final double CONTENT_HEIGHT = PAGE_HEIGHT - 2 * PAGE_PADDING;
    private static final double SIGNATURE_HEIGHT = 110;

    private static final double SUMMARY_WIDTH = 700;
    private static final String TOTAL_BORDER =
            "-fx-border-color: black transparent black transparent; -fx-border-width: 1;";

    @FXML private BorderPane root;
    @FXML private ComboBox<Integer> years;
    @FXML private ComboBox<String> quarters;
    @FXML private ToggleGroup rangeGroup;
    @FXML private RadioButton wholeYearRadio;
    @FXML private RadioButton quarterRadio;
    @FXML private CheckBox includeSummary;
    @FXML private CheckBox includeSales;
    @FXML private CheckBox includePurchases;
    @FXML private Text coverageText;
    @FXML private Text missingText;
    @FXML private Text previewSubtitle;
    @FXML private VBox previewPage;
    @FXML private ScrollPane previewScrollPane;
    @FXML private Label zoomLabel;
    @FXML private Button zoomInBtn;
    @FXML private Button zoomOutBtn;
    @FXML private Button resetZoomBtn;
    @FXML private Group previewZoomGroup;
    @FXML private Button prevPageBtn;
    @FXML private Button nextPageBtn;
    @FXML private Label pageLabel;
    @FXML private VBox salesColumnBox;
    @FXML private VBox purchaseColumnBox;

    private static final double MIN_ZOOM = 0.50;
    private static final double MAX_ZOOM = 2.00;
    private static final double ZOOM_STEP = 0.10;
    private double currentZoom = 1.00;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH);

    private final Scale previewScale = new Scale(1.0, 1.0, 0, 0);
    private SlspRepository repo;
    private final Map<Integer, Supplier> suppliersById = new HashMap<>();
    private List<SLSP> loaded = List.of();

    private List<PhysPage> sheets = List.of();
    private int pageIndex = 0;

    // ---------------------------------------------------------
    // COLUMN DEFINITIONS (copied from the exporter's layouts)
    // ---------------------------------------------------------

    private record Col(String header, Double rate, double width, Pos align, boolean wrap) {}

    private static final Col[] PURCHASE_COLS = {
        new Col("Date", null, 10.7, Pos.CENTER, false),
        new Col("Trade Name", null, 26.3, Pos.CENTER_LEFT, true),
        new Col("Address", null, 37.3, Pos.CENTER_LEFT, true),
        new Col("SALES INVOICE NO.", null, 10.7, Pos.CENTER, true),
        new Col("TIN", null, 14.6, Pos.CENTER, true),
        new Col("VAT Exempt", null, 12.4, Pos.CENTER_RIGHT, false),
        new Col("Zero-rated", null, 12.4, Pos.CENTER_RIGHT, false),
        new Col("VAT Purchases", 1.12, 14.6, Pos.CENTER_RIGHT, false),
        new Col("Input Tax", 0.12, 12.3, Pos.CENTER_RIGHT, false),
        new Col("Total Amount of Vat", null, 14.0, Pos.CENTER_RIGHT, false),
        new Col("Total Amount of Purchases", null, 16.0, Pos.CENTER_RIGHT, false)
    };

    private static final Col[] SALES_COLS = {
        new Col("Date", null, 10.7, Pos.CENTER, false),
        new Col("Sales Invoice No.", null, 18.0, Pos.CENTER, true),
        new Col("VAT Exempt", null, 14.0, Pos.CENTER_RIGHT, false),
        new Col("Zero-rated", null, 14.0, Pos.CENTER_RIGHT, false),
        new Col("VAT Receipts", 1.12, 14.6, Pos.CENTER_RIGHT, false),
        new Col("Output Tax", 0.12, 14.0, Pos.CENTER_RIGHT, false),
        new Col("Total Amount", null, 16.0, Pos.CENTER_RIGHT, false)
    };

    private final boolean[] salesVisible = allTrue(SALES_COLS.length);
    private final boolean[] purchaseVisible = allTrue(PURCHASE_COLS.length);
    private boolean[] curVisible = salesVisible;   // columns of the sheet being rendered / measured
    private Col[] curCols = SALES_COLS;

    private static boolean[] allTrue(int n) {
        boolean[] a = new boolean[n];
        Arrays.fill(a, true);
        return a;
    }

    private enum AmtStyle { ROW, SUB, TOTAL }

    // ---------------------------------------------------------
    // PAGINATION MODEL
    // ---------------------------------------------------------

    private enum RowType { DATA, SUB, SPACER }

    /** One cell of a logical row; col/span refer to the ORIGINAL (unfiltered) columns. */
    private record Cs(int col, int span, String text, Pos align, boolean bold, boolean wrap,
                      AmtStyle style, boolean label) {}

    /** base = the typed values of a transaction row (used for running totals), null otherwise. */
    private record LRow(RowType type, List<Cs> cells, BigDecimal[] base, double height) {}

    private record Body(List<LRow> rows, BigDecimal[] grand) {}

    private record PageSpec(List<LRow> rows, BigDecimal[] broughtForward, BigDecimal[] carriedForward,
                            boolean last, BigDecimal[] grand) {}

    private record SummaryKey(int year, int quarter) {}

    private record SumTable(GridPane grid, BigDecimal tax) {}

    /** One physical A4 page. For the yearly summary pages, kind/slsp/spec are null and summary is set. */
    private record PhysPage(SlspExcelExporter.Kind kind, SLSP slsp, int part, int parts,
                            PageSpec spec, SummaryKey summary) {
        PhysPage(SlspExcelExporter.Kind kind, SLSP slsp, int part, int parts, PageSpec spec) {
            this(kind, slsp, part, parts, spec, null);
        }

        boolean isSummary() { return summary != null; }
    }

    private record Res(int gridCol, int count, double rel) {}

    // ---------------------------------------------------------
    // SMALL HELPERS
    // ---------------------------------------------------------

    private static String formatDate(LocalDate date) {
        return DATE_FORMAT.format(date);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int countVisible(boolean[] v) {
        int n = 0;
        for (boolean b : v) if (b) n++;
        return n;
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    private static BigDecimal[] plus(BigDecimal[] a, BigDecimal[] b) {
        BigDecimal[] r = new BigDecimal[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i].add(b[i]);
        return r;
    }

    private static String ordinal(int quarter) {
        return switch (quarter) {
            case 1 -> "1ST";
            case 2 -> "2ND";
            case 3 -> "3RD";
            default -> "4TH";
        };
    }

    /** Grid column index of an original column, or -1 if it is hidden. */
    private int vcol(int col) {
        if (col < 0 || col >= curVisible.length || !curVisible[col]) return -1;
        int n = 0;
        for (int i = 0; i < col; i++) if (curVisible[i]) n++;
        return n;
    }

    /** Nearest visible original column, looking left first, then right. -1 if none. */
    private int nearestVisible(int col) {
        for (int i = Math.min(col, curVisible.length - 1); i >= 0; i--) if (curVisible[i]) return i;
        for (int i = col + 1; i < curVisible.length; i++) if (curVisible[i]) return i;
        return -1;
    }

    // ---------------------------------------------------------
    // ZOOM
    // ---------------------------------------------------------

    private void handlePreviewZoom(ScrollEvent event) {
        if (!event.isControlDown()) return;
        double deltaY = event.getDeltaY();
        if (deltaY == 0) return;
        setZoom(deltaY > 0 ? currentZoom + ZOOM_STEP : currentZoom - ZOOM_STEP);
        event.consume();
    }

    private void handleShortcut(javafx.scene.input.KeyEvent event) {
        if (event.isControlDown()) {
            switch (event.getCode()) {
                case EQUALS, PLUS, ADD -> { zoomIn(); event.consume(); }
                case MINUS, SUBTRACT -> { zoomOut(); event.consume(); }
                case DIGIT0, NUMPAD0 -> { resetZoom(); event.consume(); }
                default -> { }
            }
        } else if (event.isAltDown()) {
            switch (event.getCode()) {
                case LEFT -> { prevPage(); event.consume(); }
                case RIGHT -> { nextPage(); event.consume(); }
                default -> { }
            }
        }
    }

    @FXML private void zoomIn() { setZoom(currentZoom + ZOOM_STEP); }
    @FXML private void zoomOut() { setZoom(currentZoom - ZOOM_STEP); }
    @FXML private void resetZoom() { setZoom(1.00); }

    private void setZoom(double zoom) {
        currentZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.round(zoom * 100.0) / 100.0));
        previewScale.setX(currentZoom);
        previewScale.setY(currentZoom);
        if (!previewPage.getTransforms().contains(previewScale)) {
            previewPage.getTransforms().add(previewScale);
        }
        if (zoomLabel != null) zoomLabel.setText(Math.round(currentZoom * 100) + "%");
        zoomInBtn.setDisable(currentZoom >= MAX_ZOOM);
        zoomOutBtn.setDisable(currentZoom <= MIN_ZOOM);
        previewZoomGroup.requestLayout();
        previewScrollPane.requestLayout();
    }

    // ---------------------------------------------------------
    // INITIALIZATION
    // ---------------------------------------------------------

    @FXML
    private void initialize() throws Exception {
        Pages.bindShortcut(root, "ESC", this::back);
        Pages.bindShortcut(root, "Ctrl+P", this::printSlsp);
        Pages.bindShortcut(root, "Ctrl+E", this::exportExcel);
        root.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, this::handleShortcut);
        root.addEventFilter(ScrollEvent.SCROLL, this::handlePreviewZoom);

        sizePage();
        setZoom(1.00);
        repo = new SlspRepository();

        for (Supplier supplier : new SupplierViewModel().loadSuppliers()) {
            if (supplier != null) suppliersById.put(supplier.getId(), supplier);
        }

        int currentYear = LocalDate.now().getYear();
        years.getItems().setAll(currentYear, currentYear - 1);
        years.setValue(currentYear);

        quarters.getItems().setAll("Q1 (Jan - Mar)", "Q2 (Apr - Jun)", "Q3 (Jul - Sep)", "Q4 (Oct - Dec)");
        quarters.getSelectionModel().select((LocalDate.now().getMonthValue() - 1) / 3);
        quarters.setDisable(!quarterRadio.isSelected());

        rangeGroup.selectedToggleProperty().addListener((_, _, _) -> {
            quarters.setDisable(!quarterRadio.isSelected());
            pageIndex = 0;
            refreshPreview();
        });
        years.valueProperty().addListener((_, _, _) -> { pageIndex = 0; refreshPreview(); });
        quarters.valueProperty().addListener((_, _, _) -> { pageIndex = 0; refreshPreview(); });
        includeSummary.selectedProperty().addListener((_, _, _) -> { pageIndex = 0; refreshPreview(); });
        includeSales.selectedProperty().addListener((_, _, _) -> { pageIndex = 0; refreshPreview(); });
        includePurchases.selectedProperty().addListener((_, _, _) -> { pageIndex = 0; refreshPreview(); });
        
        buildColumnChecklist(salesColumnBox, "Sales columns", SALES_COLS, salesVisible);
        buildColumnChecklist(purchaseColumnBox, "Purchases columns", PURCHASE_COLS, purchaseVisible);

        salesColumnBox.visibleProperty().bind(includeSales.selectedProperty());
        salesColumnBox.managedProperty().bind(salesColumnBox.visibleProperty());
        purchaseColumnBox.visibleProperty().bind(includePurchases.selectedProperty());
        purchaseColumnBox.managedProperty().bind(purchaseColumnBox.visibleProperty());

        refreshPreview();
    }

    private void sizePage() {
        previewPage.setSpacing(0);
        previewPage.setMinWidth(PAGE_WIDTH);
        previewPage.setPrefWidth(PAGE_WIDTH);
        previewPage.setMaxWidth(PAGE_WIDTH);
        previewPage.setMinHeight(PAGE_HEIGHT);
        previewPage.setPrefHeight(PAGE_HEIGHT);
        previewPage.setMaxHeight(PAGE_HEIGHT);
    }

    // ---------------------------------------------------------
    // COLUMN CHECKLIST
    // ---------------------------------------------------------

    private void buildColumnChecklist(VBox box, String title, Col[] cols, boolean[] visible) {
        Text heading = new Text(title);
        heading.setFont(Font.font("System", FontWeight.BOLD, 13));
        box.getChildren().add(heading);

        for (int i = 0; i < cols.length; i++) {
            final int idx = i;
            CheckBox cb = new CheckBox(cols[i].header());
            cb.setSelected(true);
            cb.setStyle("-fx-text-fill: black;");
            cb.selectedProperty().addListener((_, _, now) -> {
                if (!now && countVisible(visible) <= 1) {   // keep at least one column
                    cb.setSelected(true);
                    return;
                }
                visible[idx] = now;
                refreshPreview();
            });
            box.getChildren().add(cb);
        }
    }

    // ---------------------------------------------------------
    // PAGING
    // ---------------------------------------------------------

    @FXML
    private void prevPage() {
        if (pageIndex > 0) { pageIndex--; renderCurrentPage(); }
    }

    @FXML
    private void nextPage() {
        if (pageIndex < sheets.size() - 1) { pageIndex++; renderCurrentPage(); }
    }

    private void renderCurrentPage() {
        renderPage(sheets.isEmpty() ? null : sheets.get(pageIndex));
        updatePager();
        previewScrollPane.setVvalue(0);
    }

    private void updatePager() {
        int n = sheets.size();
        if (n == 0) {
            pageLabel.setText("Page 0 of 0");
        } else {
            PhysPage sp = sheets.get(pageIndex);
            if (sp.isSummary()) {
                pageLabel.setText("Page " + (pageIndex + 1) + " of " + n + "  (Summary "
                        + sp.summary().year() + ", " + ordinal(sp.summary().quarter()) + " QTR)");
            } else {
                YearMonth ym = sp.slsp().getYearMonth();
                String abbr = ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT);
                pageLabel.setText("Page " + (pageIndex + 1) + " of " + n + "  (" + abbr + " " + ym.getYear() + " "
                        + (sp.kind() == SlspExcelExporter.Kind.SALES ? "SALES" : "PURCHASES")
                        + (sp.parts() > 1 ? ", " + sp.part() + "/" + sp.parts() : "") + ")");
            }
        }
        prevPageBtn.setDisable(pageIndex <= 0);
        nextPageBtn.setDisable(pageIndex >= n - 1);
    }

    // ---------------------------------------------------------
    // DATE RANGE
    // ---------------------------------------------------------

    private int firstMonth() {
        return quarterRadio.isSelected()
                ? quarters.getSelectionModel().getSelectedIndex() * 3 + 1 : 1;
    }

    private int lastMonth() {
        return quarterRadio.isSelected() ? firstMonth() + 2 : 12;
    }

    private String rangeLabel() {
        Integer year = years.getValue();
        if (year == null) return "";
        if (quarterRadio.isSelected()) {
            String quarter = quarters.getValue();
            return quarter == null ? String.valueOf(year) : quarter + " " + year;
        }
        return "January - December " + year;
    }

    private String getPeriodStart() {
        return YearMonth.of(years.getValue(), firstMonth()).toString();
    }

    private String getPeriodEnd() {
        return YearMonth.of(years.getValue(), lastMonth()).toString();
    }

    // ---------------------------------------------------------
    // PREVIEW
    // ---------------------------------------------------------

    private void showMessage(String message) {
        sheets = List.of();
        pageIndex = 0;
        sizePage();
        previewPage.setAlignment(Pos.TOP_LEFT);
        previewPage.getChildren().setAll(new Text(message));
        updatePager();
    }

    private void refreshPreview() {
        Integer year = years.getValue();

        if (year == null
                || (quarterRadio.isSelected()
                && quarters.getSelectionModel().getSelectedIndex() < 0)) {
            showMessage("Select a year and range to preview.");
            return;
        }

        Taxpayer taxpayer = Taxpayer.getTaxpayer();
        if (taxpayer == null) {
            loaded = List.of();
            showMessage("No taxpayer is currently selected.");
            return;
        }

        try {
            loaded = repo.getSLSPsByTaxIdBetween(taxpayer.getId(), getPeriodStart(), getPeriodEnd());
            if (loaded == null) loaded = List.of();
        } catch (Exception ex) {
            ex.printStackTrace();
            loaded = List.of();
        }

        int expectedMonths = lastMonth() - firstMonth() + 1;
        coverageText.setText(loaded.size() + " of " + expectedMonths + " months have an SLSP");
        missingText.setText(missingMonths(year));
        previewSubtitle.setText(rangeLabel());

        if (loaded.isEmpty()) {
            showMessage("No SLSP records in this range.");
            return;
        }
        if (!includeSales.isSelected() && !includePurchases.isSelected()) {
            showMessage("Select Sales and/or Purchases to preview.");
            return;
        }

        // Same order as the Excel export: all Sales sheets, all Purchases sheets, then the SLSP summary
        List<SLSP> sorted = new ArrayList<>();
        for (SLSP s : loaded) if (s != null) sorted.add(s);
        sorted.sort(Comparator.comparing(SLSP::getYearMonth));

        List<PhysPage> pages = new ArrayList<>();
        if (includeSummary.isSelected()) {
            pages.addAll(summaryPages());   // first pages
        }
        if (includeSales.isSelected()) {
            for (SLSP s : sorted) pages.addAll(paginate(SlspExcelExporter.Kind.SALES, s));
        }
        if (includePurchases.isSelected()) {
            for (SLSP s : sorted) pages.addAll(paginate(SlspExcelExporter.Kind.PURCHASES, s));
        }

        sheets = pages;
        pageIndex = Math.max(0, Math.min(pageIndex, sheets.size() - 1));

        renderCurrentPage();
    }

    private String missingMonths(int year) {
        StringBuilder missing = new StringBuilder();
        for (int month = firstMonth(); month <= lastMonth(); month++) {
            String period = YearMonth.of(year, month).toString();
            boolean found = loaded.stream().anyMatch(slsp -> period.equals(slsp.getPeriod()));
            if (!found) {
                if (missing.length() > 0) missing.append(", ");
                missing.append(YearMonth.of(year, month).getMonth()
                        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            }
        }
        return missing.length() == 0 ? "" : "No SLSP for: " + missing;
    }

    // ---------------------------------------------------------
    // NAMES / FORMATTING
    // ---------------------------------------------------------

    private String ownerName(Taxpayer taxpayer) {
        Name n = taxpayer.getName();
        if (n == null) return "";
        String full = String.join(" ",
                n.getFirstName() == null ? "" : n.getFirstName(),
                n.getMiddleName() == null ? "" : n.getMiddleName(),
                n.getLastName() == null ? "" : n.getLastName(),
                n.getSuffix() == null ? "" : n.getSuffix());
        return full.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    /** "LASTNAME, FIRSTNAME y MIDDLENAME", as on the quarterly summary. */
    private String ownerLegalName(Taxpayer taxpayer) {
        Name n = taxpayer.getName();
        if (n == null) return "";
        String last = n.getLastName() == null ? "" : n.getLastName().trim().toUpperCase(Locale.ROOT);
        String first = n.getFirstName() == null ? "" : n.getFirstName().trim().toUpperCase(Locale.ROOT);
        String middle = n.getMiddleName() == null ? "" : n.getMiddleName().trim().toUpperCase(Locale.ROOT);
        String name = last + ", " + first;
        return middle.isEmpty() ? name : name + " y " + middle;
    }

    private String supplierName(Purchase p) {
        Supplier s = suppliersById.get(p.getSupplierId());
        return s != null && s.getTradeName() != null ? s.getTradeName().trim() : "Unknown Supplier";
    }

    private static String accounting(BigDecimal v) {
        BigDecimal x = nz(v).setScale(2, RoundingMode.HALF_UP);
        if (x.signum() == 0) return "-";
        return x.signum() < 0 ? "(" + MONEY.format(x.abs()) + ")" : MONEY.format(x);
    }

    private static String peso(BigDecimal v) {
        BigDecimal x = nz(v).setScale(2, RoundingMode.HALF_UP);
        if (x.signum() == 0) return "\u20B1 -";
        return x.signum() < 0 ? "\u20B1 (" + MONEY.format(x.abs()) + ")" : "\u20B1 " + MONEY.format(x);
    }

    // ---------------------------------------------------------
    // COLUMN RESOLVING / MEASURING (respects the column checklist)
    // ---------------------------------------------------------

    private double visibleRel() {
        double s = 0;
        for (int i = 0; i < curCols.length; i++) if (curVisible[i]) s += curCols[i].width();
        return s;
    }

    /** Where a cell goes in the filtered grid; null if it must not be shown. */
    private Res resolve(Cs c) {
        int first = -1, count = 0;
        double rel = 0;
        for (int i = c.col(); i < c.col() + c.span() && i < curVisible.length; i++) {
            if (curVisible[i]) {
                if (first < 0) first = i;
                count++;
                rel += curCols[i].width();
            }
        }
        if (first < 0) {
            if (!c.label()) return null;
            first = nearestVisible(c.col());
            if (first < 0) return null;
            count = 1;
            rel = curCols[first].width();
        }
        return new Res(vcol(first), count, rel);
    }

    private double textHeight(String s, boolean bold, double size, double wrapWidth) {
        Text t = new Text(s == null ? "" : s);
        t.setFont(Font.font("Calibri", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        if (wrapWidth > 0) t.setWrappingWidth(Math.max(10, wrapWidth));
        return t.getLayoutBounds().getHeight();
    }

    private double cellHeight(Cs c) {
        Res r = resolve(c);
        if (r == null) return 0;
        double w = CONTENT_WIDTH * r.rel() / visibleRel() - 10;
        return textHeight(c.text(), c.bold(), 10, c.wrap() ? w : 0) + 8;
    }

    private LRow row(RowType type, List<Cs> cells, BigDecimal[] base) {
        double h = type == RowType.SPACER ? 8 : 0;
        for (Cs c : cells) h = Math.max(h, cellHeight(c));
        return new LRow(type, cells, base, h);
    }

    private double headerHeight() {
        double rel = visibleRel(), h = 0;
        for (int i = 0; i < curCols.length; i++) {
            if (!curVisible[i]) continue;
            double th = textHeight(curCols[i].header(), false, 10, CONTENT_WIDTH * curCols[i].width() / rel - 10) + 8;
            h = Math.max(h, curCols[i].rate() == null ? th : th + 21);
        }
        return h;
    }

    private double topBlockHeight(Taxpayer tp, boolean sales, YearMonth ym) {
        String fullMonth = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ROOT);
        double h = 0;
        h += textHeight(tp.getTradeName(), false, 11, CONTENT_WIDTH);
        h += textHeight(ownerName(tp), false, 11, CONTENT_WIDTH);
        h += textHeight(tp.getBussAddress(), false, 11, CONTENT_WIDTH);
        h += textHeight("TIN", true, 11, CONTENT_WIDTH);
        h += 12;
        h += textHeight((sales ? "VAT SALES" : "VAT PURCHASES") + " FOR THE MONTH OF " + fullMonth + ", " + ym.getYear(),
                true, 11, CONTENT_WIDTH);
        h += 8;
        return h;
    }

    // ---------------------------------------------------------
    // LOGICAL ROWS (same formulas as the exporter)
    // ---------------------------------------------------------

    private Cs txt(int col, int span, String t, Pos a, boolean bold, boolean wrap, boolean label) {
        return new Cs(col, span, t == null ? "" : t, a, bold, wrap, null, label);
    }

    private Cs amt(int col, BigDecimal v, AmtStyle st) {
        return new Cs(col, 1, st == AmtStyle.TOTAL ? peso(v) : accounting(v), Pos.CENTER_RIGHT, true, false, st, false);
    }

    /** Sales base = {exempt, zero-rated, total}; Purchases base = {exempt, zero-rated, total amount of VAT}. */
    private List<Cs> amountCells(boolean sales, BigDecimal[] b, AmtStyle st) {
        List<Cs> out = new ArrayList<>();
        if (sales) {
            BigDecimal e = b[2].subtract(b[0]).subtract(b[1]).divide(VAT_DIVISOR, 10, RoundingMode.HALF_UP);
            BigDecimal f = e.multiply(VAT_RATE);
            out.add(amt(2, b[0], st));
            out.add(amt(3, b[1], st));
            out.add(amt(4, e, st));
            out.add(amt(5, f, st));
            out.add(amt(6, b[2], st));
        } else {
            BigDecimal h = b[2].divide(VAT_DIVISOR, 10, RoundingMode.HALF_UP);
            BigDecimal i = h.multiply(VAT_RATE);
            BigDecimal k = b[0].add(b[1]).add(b[2]);
            out.add(amt(5, b[0], st));
            out.add(amt(6, b[1], st));
            out.add(amt(7, h, st));
            out.add(amt(8, i, st));
            out.add(amt(9, b[2], st));
            out.add(amt(10, k, st));
        }
        return out;
    }

    private Cs labelCell(boolean sales, String text) {
        return sales ? txt(0, 2, text, Pos.CENTER_LEFT, true, false, true)
                     : txt(2, 3, text, Pos.CENTER_LEFT, true, false, true);
    }

    private LRow carryRow(boolean sales, String label, BigDecimal[] base) {
        List<Cs> cells = new ArrayList<>();
        cells.add(labelCell(sales, label));
        cells.addAll(amountCells(sales, base, AmtStyle.SUB));
        return row(RowType.SUB, cells, null);
    }

    private LRow totalRow(boolean sales, BigDecimal[] base) {
        List<Cs> cells = new ArrayList<>();
        cells.add(labelCell(sales, "Total   " + "- ".repeat(sales ? 10 : 37).strip()));
        cells.addAll(amountCells(sales, base, AmtStyle.TOTAL));
        return row(RowType.SUB, cells, null);
    }

    private Body buildBody(boolean sales, SLSP slsp) {
        List<LRow> rows = new ArrayList<>();
        BigDecimal[] grand = zeros(3);

        if (sales) {
            List<Sale> list = new ArrayList<>(slsp.getSales() == null ? List.of() : slsp.getSales());
            list.sort(Comparator
                    .comparing((Sale s) -> LocalDate.parse(s.getSaleDate()))
                    .thenComparing(s -> String.valueOf(s.getSiNum())));

            for (Sale s : list) {
                BigDecimal[] base = { nz(s.getExemptAmount()), nz(s.getZeroRatedAmount()), nz(s.getGrossAmount()) };
                List<Cs> cells = new ArrayList<>();
                cells.add(txt(0, 1, formatDate(LocalDate.parse(s.getSaleDate())), Pos.CENTER, false, false, false));
                cells.add(txt(1, 1, s.getSiNum(), Pos.CENTER, false, true, false));
                cells.addAll(amountCells(true, base, AmtStyle.ROW));
                rows.add(row(RowType.DATA, cells, base));
                grand = plus(grand, base);
            }
            rows.add(row(RowType.SPACER, List.of(), null));
            return new Body(rows, grand);
        }

        List<Purchase> all = new ArrayList<>(slsp.getPurchases() == null ? List.of() : slsp.getPurchases());
        all.sort(Comparator
                .comparing((Purchase p) -> supplierName(p).toLowerCase(Locale.ROOT))
                .thenComparingInt(Purchase::getSupplierId)
                .thenComparing(p -> LocalDate.parse(p.getPurchaseDate()))
                .thenComparing(p -> String.valueOf(p.getInvoiceNum())));

        Map<Integer, List<Purchase>> groups = new LinkedHashMap<>();
        for (Purchase p : all) groups.computeIfAbsent(p.getSupplierId(), _ -> new ArrayList<>()).add(p);

        for (List<Purchase> group : groups.values()) {
            BigDecimal[] sub = zeros(3);
            for (Purchase p : group) {
                Supplier s = suppliersById.get(p.getSupplierId());
                BigDecimal f = nz(p.getExemptAmount());
                BigDecimal z = nz(p.getZeroRatedAmount());
                BigDecimal[] base = { f, z, nz(p.getGrossAmount()).subtract(f).subtract(z) };

                List<Cs> cells = new ArrayList<>();
                cells.add(txt(0, 1, formatDate(LocalDate.parse(p.getPurchaseDate())), Pos.CENTER, false, false, false));
                cells.add(txt(1, 1, s != null ? s.getTradeName() : "Unknown Supplier", Pos.CENTER_LEFT, false, true, false));
                cells.add(txt(2, 1, s != null ? s.getBussAddress() : "", Pos.CENTER_LEFT, false, true, false));
                cells.add(txt(3, 1, p.getInvoiceNum(), Pos.CENTER, false, true, false));
                cells.add(txt(4, 1, s != null ? s.getTinNum() : "", Pos.CENTER, false, true, false));
                cells.addAll(amountCells(false, base, AmtStyle.ROW));
                rows.add(row(RowType.DATA, cells, base));
                sub = plus(sub, base);
            }
            if (group.size() > 1) {
                List<Cs> cells = new ArrayList<>();
                cells.add(txt(4, 1, "Sub Total - - -", Pos.CENTER, true, false, true));
                cells.addAll(amountCells(false, sub, AmtStyle.SUB));
                rows.add(row(RowType.SUB, cells, null));   // not added to the running total (already counted)
            }
            grand = plus(grand, sub);
            rows.add(row(RowType.SPACER, List.of(), null));
        }
        return new Body(rows, grand);
    }

    // ---------------------------------------------------------
    // PAGINATION (A4 landscape, carry forward)
    // ---------------------------------------------------------

    private List<PhysPage> paginate(SlspExcelExporter.Kind kind, SLSP slsp) {
        boolean sales = kind == SlspExcelExporter.Kind.SALES;
        curVisible = sales ? salesVisible : purchaseVisible;
        curCols = sales ? SALES_COLS : PURCHASE_COLS;

        Taxpayer taxpayer = Taxpayer.getTaxpayer();
        Body body = buildBody(sales, slsp);

        double top = topBlockHeight(taxpayer, sales, slsp.getYearMonth()) + headerHeight();
        double avail = CONTENT_HEIGHT - top - 12 - 18;   // safety margin + page-number footer
        double cfH = carryRow(sales, "Carried Forward - - -", zeros(3)).height();
        double tailH = totalRow(sales, body.grand()).height() + SIGNATURE_HEIGHT;

        List<List<LRow>> pageRows = new ArrayList<>();
        List<BigDecimal[]> bfs = new ArrayList<>();
        List<BigDecimal[]> cfs = new ArrayList<>();

        BigDecimal[] cum = zeros(3);
        List<LRow> cur = new ArrayList<>();
        BigDecimal[] curBf = null;
        double used = 0;

        for (LRow r : body.rows()) {
            if (r.type() == RowType.SPACER && cur.isEmpty()) continue;   // no blank line at the top of a page

            // keep room for the "Carried Forward" row at the bottom of this page
            if (!cur.isEmpty() && used + r.height() + cfH > avail) {
                pageRows.add(cur);
                bfs.add(curBf);
                cfs.add(cum);
                cur = new ArrayList<>();
                curBf = cum;
                used = cfH;                                  // the "Brought Forward" row
                if (r.type() == RowType.SPACER) continue;
            }
            cur.add(r);
            used += r.height();
            if (r.base() != null) cum = plus(cum, r.base());
        }

        // the Total row + signature must fit on the last page, otherwise they get their own page
        if (!cur.isEmpty() && used + tailH > avail) {
            pageRows.add(cur);
            bfs.add(curBf);
            cfs.add(cum);
            cur = new ArrayList<>();
            curBf = cum;
        }
        pageRows.add(cur);
        bfs.add(curBf);
        cfs.add(null);

        List<PhysPage> pages = new ArrayList<>();
        int n = pageRows.size();
        for (int i = 0; i < n; i++) {
            PageSpec spec = new PageSpec(pageRows.get(i), bfs.get(i), cfs.get(i), i == n - 1, body.grand());
            pages.add(new PhysPage(kind, slsp, i + 1, n, spec));
        }
        return pages;
    }

    // ---------------------------------------------------------
    // RENDERING ONE PHYSICAL PAGE
    // ---------------------------------------------------------

    private void renderPage(PhysPage page) {
        previewPage.getChildren().clear();
        sizePage();
        if (page == null) return;

        if (page.isSummary()) {
            renderSummary(page.summary());
            return;
        }
        previewPage.setAlignment(Pos.TOP_LEFT);

        Taxpayer taxpayer = Taxpayer.getTaxpayer();
        if (taxpayer == null) return;

        boolean sales = page.kind() == SlspExcelExporter.Kind.SALES;
        curVisible = sales ? salesVisible : purchaseVisible;
        curCols = sales ? SALES_COLS : PURCHASE_COLS;

        YearMonth ym = page.slsp().getYearMonth();
        String fullMonth = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ROOT);

        // header block, repeated on every page
        previewPage.getChildren().add(banner(taxpayer.getTradeName(), "Times New Roman", false));
        previewPage.getChildren().add(banner(ownerName(taxpayer), "Times New Roman", false));
        previewPage.getChildren().add(banner(taxpayer.getBussAddress(), "Times New Roman", false));
        previewPage.getChildren().add(banner(
                "TIN   " + (taxpayer.getTinNum() == null ? "" : taxpayer.getTinNum().trim()), "Calibri", true));

        Region gap = new Region();
        gap.setMinHeight(12);
        gap.setPrefHeight(12);
        previewPage.getChildren().add(gap);

        previewPage.getChildren().add(banner(
                (sales ? "VAT SALES" : "VAT PURCHASES") + " FOR THE MONTH OF " + fullMonth + ", " + ym.getYear(),
                "Calibri", true));

        Region gap2 = new Region();
        gap2.setMinHeight(8);
        gap2.setPrefHeight(8);
        previewPage.getChildren().add(gap2);

        PageSpec spec = page.spec();
        GridPane grid = newExcelGrid(curCols);
        int r = 2;
        if (spec.broughtForward() != null) {
            addRow(grid, carryRow(sales, "Brought Forward - - -", spec.broughtForward()), r++);
        }
        for (LRow row : spec.rows()) addRow(grid, row, r++);
        if (spec.carriedForward() != null) {
            addRow(grid, carryRow(sales, "Carried Forward - - -", spec.carriedForward()), r++);
        }
        if (spec.last()) addRow(grid, totalRow(sales, spec.grand()), r++);
        previewPage.getChildren().add(grid);

        if (spec.last()) previewPage.getChildren().add(signatureBlock(taxpayer));

        // page number pinned to the bottom
        Region filler = new Region();
        VBox.setVgrow(filler, Priority.ALWAYS);
        previewPage.getChildren().add(filler);
        if (page.parts() > 1) {
            Label footer = new Label("Page " + page.part() + " of " + page.parts());
            footer.setFont(Font.font("Calibri", 9));
            footer.setMaxWidth(Double.MAX_VALUE);
            footer.setAlignment(Pos.CENTER_RIGHT);
            previewPage.getChildren().add(footer);
        }
    }

    private void addRow(GridPane g, LRow row, int gridRow) {
        if (row.type() == RowType.SPACER) {
            Region sp = new Region();
            sp.setMinHeight(row.height());
            GridPane.setColumnSpan(sp, Math.max(1, countVisible(curVisible)));
            g.add(sp, 0, gridRow);
            return;
        }
        for (Cs c : row.cells()) {
            Res res = resolve(c);
            if (res == null) continue;
            Label l = cell(c.text(), c.align(), c.bold(), c.wrap(), false);
            if (c.style() == AmtStyle.TOTAL) {
                l.setStyle(TOTAL_BORDER);
            }
            GridPane.setColumnSpan(l, res.count());
            g.add(l, res.gridCol(), gridRow);
        }
    }

    private Label banner(String text, String font, boolean bold) {
        Label l = new Label(text == null ? "" : text.trim());
        l.setFont(Font.font(font, bold ? FontWeight.BOLD : FontWeight.NORMAL, 11));
        l.setMaxWidth(Double.MAX_VALUE);
        l.setAlignment(Pos.CENTER);
        l.setTextAlignment(TextAlignment.CENTER);
        l.setWrapText(true);
        return l;
    }

    private GridPane newExcelGrid(Col[] cols) {
        GridPane grid = new GridPane();
        grid.setMaxWidth(Double.MAX_VALUE);

        double sum = 0;
        for (int i = 0; i < cols.length; i++) if (curVisible[i]) sum += cols[i].width();
        for (int i = 0; i < cols.length; i++) {
            if (!curVisible[i]) continue;
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 * cols[i].width() / sum);
            grid.getColumnConstraints().add(cc);
        }

        // Two-row header (rows 0 and 1), like the merged header cells in Excel
        int v = 0;
        for (int i = 0; i < cols.length; i++) {
            if (!curVisible[i]) continue;
            Col c = cols[i];
            Label top = cell(c.header(), Pos.CENTER, false, true, true);
            if (c.rate() == null) {
                GridPane.setRowSpan(top, 2);
                grid.add(top, v, 0);
            } else {
                grid.add(top, v, 0);
                String rate = c.rate() >= 1 ? String.format(Locale.ENGLISH, "%.2f", c.rate()) : "12%";
                grid.add(cell(rate, Pos.CENTER, false, false, true), v, 1);
            }
            v++;
        }
        return grid;
    }

    private Label cell(String text, Pos align, boolean bold, boolean wrap, boolean border) {
        Label l = new Label(text == null ? "" : text);
        l.setFont(Font.font("Calibri", bold ? FontWeight.BOLD : FontWeight.NORMAL, 10));
        l.setAlignment(align);
        l.setTextAlignment(align == Pos.CENTER ? TextAlignment.CENTER
                : align == Pos.CENTER_RIGHT ? TextAlignment.RIGHT : TextAlignment.LEFT);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setMaxHeight(Double.MAX_VALUE);
        l.setWrapText(wrap);
        l.setPadding(new Insets(3, 4, 3, 4));
        if (border) l.setStyle("-fx-border-color: black; -fx-border-width: 1;");
        GridPane.setHalignment(l, align == Pos.CENTER_RIGHT ? HPos.RIGHT
                : align == Pos.CENTER ? HPos.CENTER : HPos.LEFT);
        GridPane.setFillWidth(l, true);
        return l;
    }

    private Text bold(String value, double size) {
        Text text = new Text(value);
        text.setFont(Font.font("System", FontWeight.BOLD, size));
        return text;
    }

    private Text small(String value) {
        Text text = new Text(value == null ? "" : value);
        text.setFont(Font.font(10));
        return text;
    }

    private HBox signatureBlock(Taxpayer taxpayer) {
        Region gap = new Region();
        gap.setMinHeight(36);

        VBox signer = new VBox(2, bold(ownerName(taxpayer), 10), small("Owner/Taxpayer"));
        signer.setAlignment(Pos.CENTER);

        VBox block = new VBox(2, small("Certified True & Correct:"), gap, signer);

        HBox row = new HBox(block);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setPadding(new Insets(24, 0, 0, 0));
        return row;
    }

    // ---------------------------------------------------------
    // SLSP YYYY SUMMARY PAGES (quarterly income / purchases / tax due)
    // ---------------------------------------------------------

    /** One summary page per quarter that has data, in year / quarter order (same as the Excel sheet). */
    private List<PhysPage> summaryPages() {
        TreeMap<Integer, TreeSet<Integer>> byYear = new TreeMap<>();
        for (SLSP s : loaded) {
            if (s == null) continue;
            YearMonth ym = s.getYearMonth();
            byYear.computeIfAbsent(ym.getYear(), _ -> new TreeSet<>())
                    .add((ym.getMonthValue() - 1) / 3 + 1);
        }
        List<PhysPage> out = new ArrayList<>();
        for (Map.Entry<Integer, TreeSet<Integer>> e : byYear.entrySet()) {
            for (int quarter : e.getValue()) {
                out.add(new PhysPage(null, null, 1, 1, null, new SummaryKey(e.getKey(), quarter)));
            }
        }
        return out;
    }

    /** {exempt, vatable} where vatable = (total - exempt - zero-rated) / 1.12, like the Excel formulas. */
    private static BigDecimal[] figures(BigDecimal exempt, BigDecimal zero, BigDecimal gross) {
        return new BigDecimal[] { exempt,
                gross.subtract(exempt).subtract(zero).divide(VAT_DIVISOR, 10, RoundingMode.HALF_UP) };
    }

    private static BigDecimal[] salesFigures(SLSP slsp) {
        BigDecimal e = BigDecimal.ZERO, z = BigDecimal.ZERO, g = BigDecimal.ZERO;
        if (slsp.getSales() != null) {
            for (Sale s : slsp.getSales()) {
                e = e.add(nz(s.getExemptAmount()));
                z = z.add(nz(s.getZeroRatedAmount()));
                g = g.add(nz(s.getGrossAmount()));
            }
        }
        return figures(e, z, g);
    }

    private static BigDecimal[] purchaseFigures(SLSP slsp) {
        BigDecimal e = BigDecimal.ZERO, z = BigDecimal.ZERO, g = BigDecimal.ZERO;
        if (slsp.getPurchases() != null) {
            for (Purchase p : slsp.getPurchases()) {
                e = e.add(nz(p.getExemptAmount()));
                z = z.add(nz(p.getZeroRatedAmount()));
                g = g.add(nz(p.getGrossAmount()));
            }
        }
        return figures(e, z, g);
    }

    private Region spacer(double height) {
        Region r = new Region();
        r.setMinHeight(height);
        r.setPrefHeight(height);
        return r;
    }

    private void renderSummary(SummaryKey key) {
        previewPage.getChildren().clear();
        sizePage();

        Taxpayer taxpayer = Taxpayer.getTaxpayer();
        if (taxpayer == null) return;

        boolean sales = includeSales.isSelected();
        boolean purchases = includePurchases.isSelected();

        Map<YearMonth, SLSP> byMonth = new HashMap<>();
        for (SLSP s : loaded) if (s != null) byMonth.put(s.getYearMonth(), s);

        List<YearMonth> months = new ArrayList<>();
        for (int m = 0; m < 3; m++) months.add(YearMonth.of(key.year(), (key.quarter() - 1) * 3 + 1 + m));

        String qtr = ordinal(key.quarter()) + " QTR " + key.year();

        VBox sheet = new VBox(0);
        sheet.setMinWidth(SUMMARY_WIDTH);
        sheet.setPrefWidth(SUMMARY_WIDTH);
        sheet.setMaxWidth(SUMMARY_WIDTH);

        sheet.getChildren().add(banner(taxpayer.getTradeName(), "Calibri", true));
        sheet.getChildren().add(banner(taxpayer.getBussAddress(), "Calibri", true));
        sheet.getChildren().add(banner(ownerLegalName(taxpayer), "Calibri", true));
        sheet.getChildren().add(banner(
                "TIN " + (taxpayer.getTinNum() == null ? "" : taxpayer.getTinNum().trim()), "Calibri", true));
        sheet.getChildren().add(spacer(14));

        SumTable salesTable = null, purchaseTable = null;

        if (sales) {
            Label title = banner("INCOME FOR THE " + qtr, "Calibri", true);
            title.setUnderline(true);
            sheet.getChildren().addAll(title, spacer(6));
            salesTable = summaryTable(new String[] {
                    "MONTHS", "Exempt Sales / Receipts", "Sales to Government",
                    "Vatable Sales / Receipts - Private", "Output Tax           12%",
                    "Total Sales/Receipts and Output Tax" }, months, byMonth, true);
            sheet.getChildren().addAll(salesTable.grid(), spacer(14));
        }

        if (purchases) {
            Label title = banner("PURCHASES FOR THE " + qtr, "Calibri", true);
            title.setUnderline(true);
            sheet.getChildren().addAll(title, spacer(6));
            purchaseTable = summaryTable(new String[] {
                    "MONTHS", "Purchases Not Qualified for Input Tax", "Domestic Purchase of Services",
                    "Domestic Purchase of Goods Other than Capital Goods", "Input Tax           12%",
                    "Total Purchases and Input Tax" }, months, byMonth, false);
            sheet.getChildren().add(purchaseTable.grid());
        }

        if (salesTable != null && purchaseTable != null) {
            Region fill = new Region();
            HBox.setHgrow(fill, Priority.ALWAYS);
            HBox due = new HBox(bold("Tax Due   - - - - - - - - - -", 11), fill,
                    bold(peso(salesTable.tax().subtract(purchaseTable.tax())), 11));
            due.setPadding(new Insets(10, 0, 0, 0));
            sheet.getChildren().add(due);
        }

        sheet.getChildren().add(signatureBlock(taxpayer));

        previewPage.setAlignment(Pos.TOP_CENTER);
        previewPage.getChildren().add(sheet);
    }

    /** Header row + three month rows + Total row of one quarterly table. */
    private SumTable summaryTable(String[] headers, List<YearMonth> months,
                                  Map<YearMonth, SLSP> byMonth, boolean sales) {
        double[] w = { 12.9, 14.7, 14.7, 14.7, 14.7, 15.7 };
        double sum = Arrays.stream(w).sum();

        GridPane g = new GridPane();
        g.setMaxWidth(Double.MAX_VALUE);
        for (double x : w) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 * x / sum);
            g.getColumnConstraints().add(cc);
        }

        for (int i = 0; i < headers.length; i++) {
            g.add(cell(headers[i], Pos.CENTER, false, true, true), i, 0);
        }

        BigDecimal[] tot = zeros(5);   // columns B..F
        int r = 1;
        for (YearMonth ym : months) {
            SLSP s = byMonth.get(ym);
            BigDecimal[] fig = s == null ? new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO }
                    : sales ? salesFigures(s) : purchaseFigures(s);

            BigDecimal b = fig[0];                       // exempt
            BigDecimal c = BigDecimal.ZERO;              // government sales / services: not tracked in the app
            BigDecimal d = fig[1];                       // vatable
            BigDecimal e = d.multiply(VAT_RATE);         // 12% tax
            BigDecimal f = b.add(c).add(d).add(e);       // total
            BigDecimal[] vals = { b, c, d, e, f };

            g.add(cell(ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                    Pos.CENTER, false, false, true), 0, r);
            for (int i = 0; i < vals.length; i++) {
                g.add(cell(accounting(vals[i]), Pos.CENTER_RIGHT, false, false, true), i + 1, r);
            }
            tot = plus(tot, vals);
            r++;
        }

        g.add(cell("Total   - - -", Pos.CENTER_LEFT, true, false, false), 0, r);
        for (int i = 0; i < tot.length; i++) {
            Label l = cell(peso(tot[i]), Pos.CENTER_RIGHT, true, false, false);
            l.setStyle(TOTAL_BORDER);
            g.add(l, i + 1, r);
        }

        return new SumTable(g, tot[3]);
    }

    // ---------------------------------------------------------
    // PRINT (one A4 landscape sheet of paper per preview page)
    // ---------------------------------------------------------

    @FXML
    public void printSlsp(ActionEvent event) {

        if (loaded.isEmpty() || sheets.isEmpty()) {
            showWarning("There is nothing to print for this range.");
            return;
        }
        if (!includeSales.isSelected() && !includePurchases.isSelected()) {
            showWarning("Select Sales and/or Purchases to print.");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            showWarning("No printer is available.");
            return;
        }
        if (!job.showPrintDialog(root.getScene().getWindow())) {
            return;
        }

        PageLayout layout = job.getPrinter().createPageLayout(
                Paper.A4, PageOrientation.LANDSCAPE, javafx.print.Printer.MarginType.DEFAULT);

        String oldStyle = previewPage.getStyle();
        boolean zoomed = previewPage.getTransforms().remove(previewScale);   // zoom must not affect printing
        previewPage.setStyle("-fx-background-color: white;");

        boolean ok = true;
        try {
            for (int i = 0; i < sheets.size() && ok; i++) {
                renderPage(sheets.get(i));
                previewPage.applyCss();
                previewPage.layout();
                ok = printCurrentPage(job, layout);
            }
        } finally {
            previewPage.setStyle(oldStyle);
            if (zoomed) previewPage.getTransforms().add(previewScale);
            renderCurrentPage();
        }

        if (ok) job.endJob(); else job.cancelJob();
    }

    /** The preview page already has the A4 landscape proportions, so it is simply scaled onto the paper. */
    private boolean printCurrentPage(PrinterJob job, PageLayout layout) {
        double scale = Math.min(layout.getPrintableWidth() / PAGE_WIDTH,
                                layout.getPrintableHeight() / PAGE_HEIGHT);
        double offsetX = (layout.getPrintableWidth() - PAGE_WIDTH * scale) / 2.0;

        Scale shrink = new Scale(scale, scale, 0, 0);
        Translate move = new Translate(offsetX - previewPage.getLayoutX(), -previewPage.getLayoutY());
        previewPage.getTransforms().addAll(move, shrink);
        try {
            return job.printPage(layout, previewPage);
        } finally {
            previewPage.getTransforms().removeAll(move, shrink);
        }
    }

    // ---------------------------------------------------------
    // EXPORT TO EXCEL
    // ---------------------------------------------------------

    @FXML
    public void exportExcel(ActionEvent event) {

        // 1. Validate records.
        if (loaded == null || loaded.isEmpty()) {
            showWarning("There is nothing to export for this range.");
            return;
        }

        // 2. Validate report selection.
        boolean salesSelected = includeSales.isSelected();
        boolean purchasesSelected = includePurchases.isSelected();

        if (!salesSelected && !purchasesSelected) {
            showWarning("Select Sales and/or Purchases to export.");
            return;
        }

        // 3. Validate the selected period.
        Integer selectedYear = years.getValue();

        if (selectedYear == null) {
            showWarning("Select a year before exporting.");
            return;
        }

        if (quarterRadio.isSelected()
                && quarters.getSelectionModel().getSelectedIndex() < 0) {
            showWarning("Select a quarter before exporting.");
            return;
        }

        // 4. Retrieve the selected taxpayer.
        Taxpayer taxpayer = Taxpayer.getTaxpayer();

        if (taxpayer == null) {
            showWarning("Select a taxpayer before exporting.");
            return;
        }

        // 5. Configure the Save dialog.
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export SLSP to Excel");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"));
        chooser.setInitialFileName(getExportFileName("SLSP", selectedYear, "xlsx"));

        File selectedFile = chooser.showSaveDialog(root.getScene().getWindow());

        if (selectedFile == null) {
            return;
        }

        // Ensure the extension is .xlsx.
        File file = selectedFile;

        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            file = new File(file.getParentFile(), file.getName() + ".xlsx");
        }

        // 6. Export using the accountant-style exporter (suppliers are already loaded in suppliersById).
        try (OutputStream out = new FileOutputStream(file)) {

            SlspExcelExporter exporter = new SlspExcelExporter(taxpayer, suppliersById);
            exporter.setVisibleColumns(SlspExcelExporter.Kind.SALES, salesVisible);
            exporter.setVisibleColumns(SlspExcelExporter.Kind.PURCHASES, purchaseVisible);
            exporter.setIncludeSummary(includeSummary.isSelected());
            exporter.write(salesSelected, purchasesSelected, loaded, out);

            Alert success = new Alert(
                    Alert.AlertType.INFORMATION,
                    "Excel export completed successfully.\n\n" + file.getAbsolutePath());

            success.setTitle("Export Successful");
            success.setHeaderText(null);

            setAlertIcon(success);
            success.showAndWait();

        } catch (Exception ex) {

            ex.printStackTrace();

            Alert error = new Alert(
                    Alert.AlertType.ERROR,
                    "Could not export the Excel file.\n\n" + ex.getMessage());

            error.setTitle("Export Failed");
            error.setHeaderText("Excel Export Failed");

            setAlertIcon(error);
            error.showAndWait();
        }
    }

    // ---------------------------------------------------------
    // TAXPAYER-BASED FILE NAMES
    // ---------------------------------------------------------

    private String getTaxpayerFilePrefix() {

        Taxpayer taxpayer = Taxpayer.getTaxpayer();

        if (taxpayer == null || taxpayer.getName() == null) {
            return "Taxpayer";
        }

        Name name = taxpayer.getName();

        String lastName = name.getLastName() == null ? "" : name.getLastName().trim();
        String firstName = name.getFirstName() == null ? "" : name.getFirstName().trim();

        String initial = firstName.isEmpty()
                ? ""
                : firstName.substring(0, 1).toUpperCase(Locale.ROOT);

        String prefix = (lastName + " " + initial).trim();

        prefix = prefix.replaceAll("[\\\\/:*?\"<>|]", "");

        return prefix.isBlank() ? "Taxpayer" : prefix;
    }

    private String getExportFileName(String reportType, int year, String extension) {

        String period;

        if (quarterRadio.isSelected()) {
            int quarterIndex = quarters.getSelectionModel().getSelectedIndex();
            period = quarterIndex >= 0 ? "Q" + (quarterIndex + 1) + " " + year : "Year " + year;
        } else {
            period = "Year " + year;
        }

        return "%s %s %s.%s".formatted(getTaxpayerFilePrefix(), reportType, period, extension);
    }

    // ---------------------------------------------------------
    // ALERTS AND NAVIGATION
    // ---------------------------------------------------------

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        setAlertIcon(alert);
        alert.showAndWait();
    }

    private void setAlertIcon(Alert alert) {
        try {
            var iconStream = getClass().getResourceAsStream("/app/views/fxml/images/logo.png");

            if (iconStream != null) {
                Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    public void back(ActionEvent event) {
        Pages.change(event, Pages.SLSP);
    }
}