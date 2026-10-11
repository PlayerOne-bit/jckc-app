package app.views;

import java.io.File;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.TextAlignment;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import app.database.SlspRepository;
import app.export.SlspExcelExporter;
import app.models.Name;
import app.models.Purchase;
import app.models.SLSP;
import app.models.Sale;
import app.models.Supplier;
import app.models.Taxpayer;
import app.viewmodels.SupplierViewModel;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.PrinterJob;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.Group;
import javafx.scene.input.ScrollEvent;
import javafx.geometry.VPos;
import javafx.scene.control.Separator;
import javafx.scene.paint.Color;
public class SlspPrintController {

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00");
    private static final Color SUBTOTAL_COLOR = Color.web("#1d4ed8");   // blue
    private static final Color TAX_DUE_COLOR = Color.web("#dc2626");    // red
    @FXML private BorderPane root;
    @FXML private ComboBox<Integer> years;
    @FXML private ComboBox<String> quarters;
    @FXML private ToggleGroup rangeGroup;
    @FXML private RadioButton wholeYearRadio;
    @FXML private RadioButton quarterRadio;
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
    private static final double MIN_ZOOM = 0.50;
    private static final double MAX_ZOOM = 2.00;
    private static final double ZOOM_STEP = 0.10;
    private double currentZoom = 1.00;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH);

    private static String formatDate(LocalDate date) {
        return DATE_FORMAT.format(date);
    }
    private final Scale previewScale = new Scale(1.0, 1.0, 0, 0);
    private SlspRepository repo;

    private final Map<Integer, String> supplierLabels = new HashMap<>();

    private List<SLSP> loaded = List.of();

    private record Totals(BigDecimal exempt, BigDecimal taxable, BigDecimal vat,
            BigDecimal gross, BigDecimal total) {

	static Totals empty() {
		return new Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
		      BigDecimal.ZERO, BigDecimal.ZERO);
		}
		
		Totals add(BigDecimal e, BigDecimal t, BigDecimal v, BigDecimal g, BigDecimal tot) {
		return new Totals(exempt.add(nz(e)), taxable.add(nz(t)), vat.add(nz(v)),
		      gross.add(nz(g)), total.add(nz(tot)));
		}
		
		Totals plus(Totals o) {
		return add(o.exempt, o.taxable, o.vat, o.gross, o.total);
		}
	}
    private void handlePreviewZoom(ScrollEvent event) {
        if (!event.isControlDown()) {
            return;
        }

        double deltaY = event.getDeltaY();

        if (deltaY == 0) {
            return;
        }

        setZoom(
            deltaY > 0
                ? currentZoom + ZOOM_STEP
                : currentZoom - ZOOM_STEP
        );

        event.consume();
    }
    private void handleZoomShortcut(javafx.scene.input.KeyEvent event) {
        if (event.isControlDown()) {
            switch (event.getCode()) {
                case EQUALS, PLUS, ADD -> {
                    zoomIn();
                    event.consume();
                }
                case MINUS, SUBTRACT -> {
                    zoomOut();
                    event.consume();
                }
                case DIGIT0, NUMPAD0 -> {
                    resetZoom();
                    event.consume();
                }
                default -> {
                    // No zoom shortcut for this key.
                }
            }
        }
    }
    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ---------------------------------------------------------
    // INITIALIZATION
    // ---------------------------------------------------------
    @FXML
    private void zoomIn() {
        setZoom(currentZoom + ZOOM_STEP);
    }

    @FXML
    private void zoomOut() {
        setZoom(currentZoom - ZOOM_STEP);
    }

    @FXML
    private void resetZoom() {
        setZoom(1.00);
    }

    private void setZoom(double zoom) {
        currentZoom = Math.max(
            MIN_ZOOM,
            Math.min(MAX_ZOOM, Math.round(zoom * 100.0) / 100.0)
        );

        previewScale.setX(currentZoom);
        previewScale.setY(currentZoom);

        if (!previewPage.getTransforms().contains(previewScale)) {
            previewPage.getTransforms().add(previewScale);
        }

        if (zoomLabel != null) {
            zoomLabel.setText(
                Math.round(currentZoom * 100) + "%"
            );
        }

        zoomInBtn.setDisable(currentZoom >= MAX_ZOOM);
        zoomOutBtn.setDisable(currentZoom <= MIN_ZOOM);

        previewZoomGroup.requestLayout();
        previewScrollPane.requestLayout();
    }
    @FXML
    private void initialize() throws Exception {
        Pages.bindShortcut(root, "ESC", this::back);
        Pages.bindShortcut(root, "Ctrl+P", this::printSlsp);
        Pages.bindShortcut(root, "Ctrl+E", this::exportExcel);
        root.addEventFilter(
        	    javafx.scene.input.KeyEvent.KEY_PRESSED,
        	    this::handleZoomShortcut
        	);
        root.addEventFilter(
            ScrollEvent.SCROLL,
            this::handlePreviewZoom
        );

        setZoom(1.00);
        repo = new SlspRepository();

        for (Supplier supplier : new SupplierViewModel().loadSuppliers()) {
            String name = supplier.getTradeName() == null ? "" : supplier.getTradeName().trim();
            String tin = supplier.getTinNum() == null ? "" : supplier.getTinNum().trim();
            supplierLabels.put(supplier.getId(), name + ": " + tin);   // "TradeName: TradeNum"
        }
        int currentYear = LocalDate.now().getYear();

        years.getItems().setAll(
                currentYear,
                currentYear - 1
        );

        years.setValue(currentYear);

        quarters.getItems().setAll(
                "Q1 (Jan - Mar)",
                "Q2 (Apr - Jun)",
                "Q3 (Jul - Sep)",
                "Q4 (Oct - Dec)"
        );

        quarters.getSelectionModel().select(
                (LocalDate.now().getMonthValue() - 1) / 3
        );

        quarters.setDisable(!quarterRadio.isSelected());

        rangeGroup.selectedToggleProperty().addListener(
        		(_,_,_) -> {
                    quarters.setDisable(!quarterRadio.isSelected());
                    refreshPreview();
                }
        );

        years.valueProperty().addListener(
        		(_,_,_) -> refreshPreview()
        );

        quarters.valueProperty().addListener(
        		(_,_,_) -> refreshPreview()
        );

        includeSales.selectedProperty().addListener(
        		(_,_,_) -> refreshPreview()
        );

        includePurchases.selectedProperty().addListener(
        		(_,_,_)-> refreshPreview()
        );

        refreshPreview();
    }

    // ---------------------------------------------------------
    // DATE RANGE
    // ---------------------------------------------------------

    private int firstMonth() {
        return quarterRadio.isSelected()
                ? quarters.getSelectionModel().getSelectedIndex() * 3 + 1
                : 1;
    }

    private int lastMonth() {
        return quarterRadio.isSelected()
                ? firstMonth() + 2
                : 12;
    }

    private String rangeLabel() {
        Integer year = years.getValue();

        if (year == null) {
            return "";
        }

        if (quarterRadio.isSelected()) {
            String quarter = quarters.getValue();

            return quarter == null
                    ? String.valueOf(year)
                    : quarter + " " + year;
        }

        return "January - December " + year;
    }

    private String getPeriodStart() {
        return YearMonth.of(
                years.getValue(),
                firstMonth()
        ).toString();
    }

    private String getPeriodEnd() {
        return YearMonth.of(
                years.getValue(),
                lastMonth()
        ).toString();
    }

    // ---------------------------------------------------------
    // PREVIEW
    // ---------------------------------------------------------

    private void refreshPreview() {

        previewPage.getChildren().clear();

        Integer year = years.getValue();

        if (year == null
                || (quarterRadio.isSelected()
                && quarters.getSelectionModel().getSelectedIndex() < 0)) {

            previewPage.getChildren().add(
                    new Text("Select a year and range to preview.")
            );

            return;
        }

        Taxpayer taxpayer = Taxpayer.getTaxpayer();

        if (taxpayer == null) {
            previewPage.getChildren().add(
                    new Text("No taxpayer is currently selected.")
            );

            loaded = List.of();
            return;
        }

        try {
            loaded = repo.getSLSPsByTaxIdBetween(
                    taxpayer.getId(),
                    getPeriodStart(),
                    getPeriodEnd()
            );

            if (loaded == null) {
                loaded = List.of();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            loaded = List.of();
        }

        int expectedMonths = lastMonth() - firstMonth() + 1;

        coverageText.setText(
                loaded.size() + " of " + expectedMonths
                        + " months have an SLSP"
        );

        missingText.setText(missingMonths(year));

        previewSubtitle.setText(rangeLabel());

        previewPage.getChildren().add(
                bold("SUMMARY LIST OF SALES AND PURCHASES", 16)
        );

        previewPage.getChildren().add(
                new Text(
                        taxpayer.getTradeName()
                                + " | TIN: " + taxpayer.getTinNum()
                )
        );

        previewPage.getChildren().add(
                new Text("For the period: " + rangeLabel())
        );
        previewPage.getChildren().add(new Separator()); 
        if (loaded.isEmpty()) {
            previewPage.getChildren().add(
                    new Text("\nNo SLSP records in this range.")
            );

            return;
        }

        Totals salesTotals = Totals.empty();
        Totals purchaseTotals = Totals.empty();
        for (SLSP slsp : loaded) {
            previewPage.getChildren().add(bold("\n" + slsp.getTitle(), 13));

            if (includeSales.isSelected()) {
                salesTotals = salesTotals.plus(sumSales(slsp));
                addSalesTable(slsp);
            }
            if (includePurchases.isSelected()) {
                purchaseTotals = purchaseTotals.plus(sumPurchases(slsp));
                addPurchasesTable(slsp);
            }
        }

        addSummary(salesTotals, purchaseTotals);
        previewPage.getChildren().add(signatureBlock(taxpayer));
    }
    private String ownerName(Taxpayer taxpayer) {
        Name n = taxpayer.getName();
        if (n == null) return "";
        String full = String.join(" ",
                n.getFirstName() == null ? "" : n.getFirstName(),
                n.getMiddleName() == null ? "" : n.getMiddleName(),
                n.getLastName() == null ? "" : n.getLastName());
        return full.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
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
    private String missingMonths(int year) {

        StringBuilder missing = new StringBuilder();

        for (int month = firstMonth(); month <= lastMonth(); month++) {

            String period = YearMonth.of(year, month).toString();

            boolean found = loaded.stream()
                    .anyMatch(slsp -> period.equals(slsp.getPeriod()));

            if (!found) {

                if (missing.length() > 0) {
                    missing.append(", ");
                }

                missing.append(
                        YearMonth.of(year, month)
                                .getMonth()
                                .getDisplayName(
                                        java.time.format.TextStyle.SHORT,
                                        Locale.ENGLISH
                                )
                );
            }
        }

        return missing.length() == 0
                ? ""
                : "No SLSP for: " + missing;
    }

    // ---------------------------------------------------------
    // CALCULATE TOTALS
    // ---------------------------------------------------------

    private Totals sumSales(SLSP slsp) {
        Totals totals = Totals.empty();
        if (slsp.getSales() == null) return totals;
        for (Sale s : slsp.getSales()) {
            totals = totals.add(s.getExemptAmount(), s.getTaxableAmount(), s.getOutputVat(),
                    s.getGrossAmount(), s.getGrossAmount());   // sale "Total Amount" = gross
        }
        return totals;
    }

    private Totals sumPurchases(SLSP slsp) {
        Totals totals = Totals.empty();
        if (slsp.getPurchases() == null) return totals;
        for (Purchase p : slsp.getPurchases()) {
            totals = totals.add(p.getExemptAmount(), p.getTaxableAmount(), p.getInputVat(),
                    p.getGrossAmount(), purchaseTotal(p));
        }
        return totals;
    }
    private static BigDecimal purchaseTotal(Purchase p) {   // VAT Purchases + Input Tax + Exempt + Zero-rated
        return nz(p.getTaxableAmount()).add(nz(p.getInputVat()))
                .add(nz(p.getExemptAmount())).add(nz(p.getZeroRatedAmount()));
    }

    private record SupplierDay(int supplierId, LocalDate date) {}
    private static int compareInvoice(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        if (!x.isEmpty() && !y.isEmpty()
                && x.chars().allMatch(Character::isDigit)
                && y.chars().allMatch(Character::isDigit)) {
            int c = new BigInteger(x).compareTo(new BigInteger(y));
            if (c != 0) return c;
        }
        return x.compareToIgnoreCase(y);
    }
    
    // ---------------------------------------------------------
    // SALES TABLE
    // ---------------------------------------------------------

    private void addSalesTable(SLSP slsp) {
        previewPage.getChildren().add(bold("Sales", 11));

        GridPane grid = newGrid(2, "Date", "SI No.", "VAT Receipts (1.12)",
                "Output Tax (12%)", "Total Amount");

        Map<LocalDate, List<Sale>> byDate = new java.util.TreeMap<>();   // oldest date first
        if (slsp.getSales() != null) {
            for (Sale s : slsp.getSales()) {
                byDate.computeIfAbsent(LocalDate.parse(s.getSaleDate()), _ -> new ArrayList<>()).add(s);
            }
        }

        Totals totals = Totals.empty();
        int row = 1;

        for (Map.Entry<LocalDate, List<Sale>> entry : byDate.entrySet()) {
            List<Sale> day = entry.getValue();
            day.sort(Comparator.comparing(Sale::getSiNum, SlspPrintController::compareInvoice));

            Totals t = Totals.empty();
            for (Sale s : day) {
                t = t.add(s.getExemptAmount(), s.getTaxableAmount(), s.getOutputVat(),
                        s.getGrossAmount(), s.getGrossAmount());
            }
            totals = totals.plus(t);

            addRow(grid, row++, new String[] {
                            formatDate(entry.getKey()),
                            invoiceRange(day.get(0).getSiNum(), day.get(day.size() - 1).getSiNum()) },
                    t.taxable(), t.vat(), t.gross());
        }

        addTotals(grid, row, 2, totals.taxable(), totals.vat(), totals.gross());
        previewPage.getChildren().add(grid);
    }

    // ---------------------------------------------------------
    // PURCHASES TABLE
    // ---------------------------------------------------------

    private void addPurchasesTable(SLSP slsp) {
        previewPage.getChildren().add(bold("Purchases", 11));

        GridPane grid = newGrid(3, "Date", "Supplier", "Invoice No.",
                "VAT Purchases (1.12)", "Input Tax (12%)",
                "Gross Taxable / Total Amt. of VAT", "Total Amount of Purchases");

        // one group per supplier per date
        Map<SupplierDay, List<Purchase>> groups = new HashMap<>();
        if (slsp.getPurchases() != null) {
            for (Purchase p : slsp.getPurchases()) {
                groups.computeIfAbsent(
                        new SupplierDay(p.getSupplierId(), LocalDate.parse(p.getPurchaseDate())),
                        _ -> new ArrayList<>()).add(p);
            }
        }

        // supplier A-Z first, then date
        List<Map.Entry<SupplierDay, List<Purchase>>> entries = new ArrayList<>(groups.entrySet());
        entries.sort(Comparator
                .comparing((Map.Entry<SupplierDay, List<Purchase>> e) ->
                        supplierLabel(e.getValue().get(0)).toLowerCase(Locale.ROOT))
                .thenComparingInt(e -> e.getKey().supplierId())
                .thenComparing(e -> e.getKey().date()));

        Totals totals = Totals.empty();
        int row = 1;

        for (Map.Entry<SupplierDay, List<Purchase>> entry : entries) {
            List<Purchase> day = entry.getValue();
            day.sort(Comparator.comparing(Purchase::getInvoiceNum, SlspPrintController::compareInvoice));

            Totals t = Totals.empty();
            for (Purchase p : day) {
                t = t.add(p.getExemptAmount(), p.getTaxableAmount(), p.getInputVat(),
                        p.getGrossAmount(), purchaseTotal(p));
            }
            totals = totals.plus(t);

            addRow(grid, row++, new String[] {
                            formatDate(entry.getKey().date()),
                            supplierLabel(day.get(0)),
                            invoiceRange(day.get(0).getInvoiceNum(),
                                    day.get(day.size() - 1).getInvoiceNum()) },
                    t.taxable(), t.vat(), t.gross(), t.total());
        }

        addTotals(grid, row, 3, totals.taxable(), totals.vat(), totals.gross(), totals.total());
        previewPage.getChildren().add(grid);
    }
    private String supplierLabel(Purchase p) {
        return supplierLabels.getOrDefault(p.getSupplierId(), "Unknown Supplier");
    }

    // ---------------------------------------------------------
    // FINAL SUMMARY
    // ---------------------------------------------------------

    private void addSummary(Totals sales, Totals purchases) {
        previewPage.getChildren().add(bold("\nSUMMARY", 13));

        GridPane grid = newGrid(1, "Description", "VAT Sales / Purchases (1.12)",
                "Output Tax (12%)", "Input Tax (12%)", "Total Amount");

        int row = 1;

        if (includeSales.isSelected()) {
            grid.add(bold("Sales", 10), 0, row);
            addAmount(grid, sales.taxable(), 1, row, false, 10);
            addAmount(grid, sales.vat(), 2, row, false, 10);
            addAmount(grid, sales.gross(), 4, row, false, 10);
            row++;
        }

        if (includePurchases.isSelected()) {
            grid.add(bold("Purchases", 10), 0, row);
            addAmount(grid, purchases.taxable(), 1, row, false, 10);
            addAmount(grid, purchases.vat(), 3, row, false, 10);
            addAmount(grid, purchases.total(), 4, row, false, 10);
            row++;
        }

        Text taxDueLabel = bold("TAX DUE", 11);
        taxDueLabel.setFill(TAX_DUE_COLOR);
        grid.add(taxDueLabel, 0, row);
        addAmount(grid, sales.vat().subtract(purchases.vat()), 4, row, true, 11, TAX_DUE_COLOR);

        previewPage.getChildren().add(grid);
    }


    // ---------------------------------------------------------
    // TABLE HELPERS
    // ---------------------------------------------------------


    private String invoiceRange(String firstRow, String lastRow) {

        String start = splitInvoice(firstRow, true);
        String end = splitInvoice(lastRow, false);

        if (start.isEmpty()) {
            return end;
        }

        if (end.isEmpty() || start.equals(end)) {
            return start;
        }

        return start + " - " + end;
    }

    private String splitInvoice(String value, boolean takeFirst) {

        if (value == null || value.isBlank()) {
            return "";
        }

        String[] parts = value.trim().split("\\s*-\\s*");

        return (
                takeFirst
                        ? parts[0]
                        : parts[parts.length - 1]
        ).trim();
    }

    private GridPane newGrid(int textColumns, String... headers) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(2);

        int growColumn = textColumns > 1 ? 1 : 0;

        for (int i = 0; i < headers.length; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(i == growColumn ? Priority.ALWAYS : Priority.SOMETIMES);
            grid.getColumnConstraints().add(cc);

            Text header = bold(headers[i], 10);
            if (i >= textColumns) {
                header.setWrappingWidth(90);
                header.setTextAlignment(TextAlignment.RIGHT);
                GridPane.setHalignment(header, HPos.RIGHT);
            }
            grid.add(header, i, 0);
        }
        Separator headerLine = new Separator();
        GridPane.setColumnSpan(headerLine, headers.length);
        GridPane.setValignment(headerLine, VPos.BOTTOM);
        grid.add(headerLine, 0, 0); 
        return grid;
    }

    private void addRow(GridPane grid, int row, String[] texts, BigDecimal... amounts) {
        for (int i = 0; i < texts.length; i++) {
            grid.add(small(texts[i]), i, row);
        }
        for (int i = 0; i < amounts.length; i++) {
            addAmount(grid, amounts[i], texts.length + i, row, false, 10);
        }
    }

    private void addTotals(GridPane grid, int row, int firstAmountColumn, BigDecimal... totals) {
        Text label = bold("SUBTOTAL", 10);
        label.setFill(SUBTOTAL_COLOR);
        grid.add(label, 0, row);
        for (int i = 0; i < totals.length; i++) {
            addAmount(grid, totals[i], firstAmountColumn + i, row, true, 10, SUBTOTAL_COLOR);
        }
    }
    private static String formatMoney(BigDecimal amount) {
        return "₱ " + MONEY.format(nz(amount));
    }

    private Text bold(String value, double size) {

        Text text = new Text(value);

        text.setFont(
                Font.font(
                        "System",
                        FontWeight.BOLD,
                        size
                )
        );

        return text;
    }
    private void addAmount(GridPane grid, BigDecimal value, int column, int row,
            boolean isBold, double size) {
    	addAmount(grid, value, column, row, isBold, size, null);
	}

	private void addAmount(GridPane grid, BigDecimal value, int column, int row,
	            boolean isBold, double size, Color color) {
		String text = value == null ? "" : formatMoney(value);
		Text cell = isBold ? bold(text, size) : small(text);
		if (color != null) cell.setFill(color);
		GridPane.setHalignment(cell, HPos.RIGHT);
		grid.add(cell, column, row);
	}
    private Text small(String value) {

        Text text = new Text(
                value == null ? "" : value
        );

        text.setFont(Font.font(10));

        return text;
    }

    // ---------------------------------------------------------
    // PRINT
    // ---------------------------------------------------------

    @FXML
    public void printSlsp(ActionEvent event) {

        if (loaded.isEmpty()) {
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

        // A4 landscape: the purchases table is wide, so this keeps the text readable
        PageLayout layout = job.getPrinter().createPageLayout(
                Paper.A4, PageOrientation.LANDSCAPE, javafx.print.Printer.MarginType.DEFAULT);

        double width = previewPage.getLayoutBounds().getWidth();
        double height = previewPage.getLayoutBounds().getHeight();
        if (width <= 0 || height <= 0) {
            showWarning("The preview is not ready to print.");
            return;
        }

        // Fit the WIDTH to the A4 page; anything taller continues on the next page
        double scale = Math.min(1.0, layout.getPrintableWidth() / width);
        double pageHeight = layout.getPrintableHeight() / scale;
        double offsetX = (layout.getPrintableWidth() - width * scale) / 2.0;
        List<Double> starts = pageStarts(pageHeight);

        Scale shrink = new Scale(scale, scale, 0, 0);
        String oldStyle = previewPage.getStyle();
        Node oldClip = previewPage.getClip();
        boolean zoomed = previewPage.getTransforms().remove(previewScale);   // zoom must not affect printing

        previewPage.setStyle("-fx-background-color: white;");
        boolean ok = true;

        try {
            for (int i = 0; i < starts.size() && ok; i++) {
                double start = starts.get(i);
                double end = i + 1 < starts.size() ? starts.get(i + 1) : height;

                previewPage.setClip(new Rectangle(0, start, width, Math.min(pageHeight, end - start)));

                Translate move = new Translate(
                        offsetX - previewPage.getLayoutX(),
                        -previewPage.getLayoutY() - start * scale);
                previewPage.getTransforms().addAll(move, shrink);
                try {
                    ok = job.printPage(layout, previewPage);
                } finally {
                    previewPage.getTransforms().removeAll(move, shrink);
                }
            }
        } finally {
            previewPage.setClip(oldClip);
            previewPage.setStyle(oldStyle);
            if (zoomed) previewPage.getTransforms().add(previewScale);
        }

        if (ok) job.endJob(); else job.cancelJob();
    }

    /** Y positions where each printed page starts, breaking between preview blocks where possible. */
    private List<Double> pageStarts(double pageHeight) {
        List<Double> starts = new ArrayList<>();
        double start = 0;
        starts.add(start);

        for (Node child : previewPage.getChildren()) {
            Bounds b = child.getBoundsInParent();
            while (b.getMaxY() - start > pageHeight + 0.5) {
                // start a new page at this block, or, if the block is taller than a page, slice it
                start = b.getMinY() > start ? b.getMinY() : start + pageHeight;
                starts.add(start);
            }
        }
        return starts;
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
                new FileChooser.ExtensionFilter(
                        "Excel Workbook (*.xlsx)",
                        "*.xlsx"
                )
        );

        chooser.setInitialFileName(
                getExportFileName(
                        "SLSP",
                        selectedYear,
                        "xlsx"
                )
        );

        File selectedFile = chooser.showSaveDialog(
                root.getScene().getWindow()
        );

        if (selectedFile == null) {
            return;
        }

        // Ensure the extension is .xlsx.
        File file = selectedFile;

        if (!file.getName()
                .toLowerCase(Locale.ROOT)
                .endsWith(".xlsx")) {

            file = new File(
                    file.getParentFile(),
                    file.getName() + ".xlsx"
            );
        }

        // 6. Warn about zero-rated purchases.
        if (purchasesSelected
                && SlspExcelExporter.hasZeroRatedPurchases(loaded)) {

            Alert warning = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Some purchases contain zero-rated amounts, but the "
                            + "accountant-style Purchases worksheet does not "
                            + "have a separate Zero-rated column.\n\n"
                            + "These amounts will not appear in a separate "
                            + "column in the exported worksheet.\n\n"
                            + "Do you want to continue?"
            );

            warning.setTitle("Zero-Rated Purchases");
            warning.setHeaderText("Zero-rated purchases detected");

            setAlertIcon(warning);

            var result = warning.showAndWait();

            if (result.isEmpty()
                    || result.get()
                            != javafx.scene.control.ButtonType.OK) {
                return;
            }
        }

        // 7. Load complete supplier objects for the exporter.
        Map<Integer, Supplier> suppliersById = new HashMap<>();

        try {

            List<Supplier> suppliers =
                    new SupplierViewModel().loadSuppliers();

            if (suppliers != null) {
                for (Supplier supplier : suppliers) {
                    if (supplier != null) {
                        suppliersById.put(
                                supplier.getId(),
                                supplier
                        );
                    }
                }
            }

        } catch (Exception ex) {

            ex.printStackTrace();

            showWarning(
                    "Could not load supplier information.\n\n"
                            + ex.getMessage()
            );

            return;
        }

        // 8. Export using the accountant-style exporter.
        try (OutputStream out = new FileOutputStream(file)) {

            SlspExcelExporter exporter = new SlspExcelExporter(
                    taxpayer,
                    suppliersById
            );

            exporter.write(
                    salesSelected,
                    purchasesSelected,
                    loaded,
                    out
            );

            Alert success = new Alert(
                    Alert.AlertType.INFORMATION,
                    "Excel export completed successfully.\n\n"
                            + file.getAbsolutePath()
            );

            success.setTitle("Export Successful");
            success.setHeaderText(null);

            setAlertIcon(success);
            success.showAndWait();

        } catch (Exception ex) {

            ex.printStackTrace();

            Alert error = new Alert(
                    Alert.AlertType.ERROR,
                    "Could not export the Excel file.\n\n"
                            + ex.getMessage()
            );

            error.setTitle("Export Failed");
            error.setHeaderText("Excel Export Failed");

            setAlertIcon(error);
            error.showAndWait();
        }
    }

    // ---------------------------------------------------------
    // SUMMARY WORKSHEET
    // ---------------------------------------------------------

    // ---------------------------------------------------------
    // GENERIC EXCEL SHEET
    // ---------------------------------------------------------

    // ---------------------------------------------------------
    // TAXPAYER-BASED FILE NAMES
    // ---------------------------------------------------------

    private String getTaxpayerFilePrefix() {

        Taxpayer taxpayer = Taxpayer.getTaxpayer();

        if (taxpayer == null || taxpayer.getName() == null) {
            return "Taxpayer";
        }

        Name name = taxpayer.getName();

        String lastName = name.getLastName() == null
                ? ""
                : name.getLastName().trim();

        String firstName = name.getFirstName() == null
                ? ""
                : name.getFirstName().trim();

        String initial = firstName.isEmpty()
                ? ""
                : firstName.substring(0, 1).toUpperCase(Locale.ROOT);

        String prefix = (lastName + " " + initial).trim();

        prefix = prefix.replaceAll("[\\\\/:*?\"<>|]", "");

        return prefix.isBlank()
                ? "Taxpayer"
                : prefix;
    }

    private String getExportFileName(
            String reportType,
            int year,
            String extension) {

        String period;

        if (quarterRadio.isSelected()) {

            int quarterIndex =
                    quarters.getSelectionModel().getSelectedIndex();

            if (quarterIndex >= 0) {
                period = "Q" + (quarterIndex + 1) + " " + year;
            } else {
                period = "Year " + year;
            }

        } else {
            period = "Year " + year;
        }

        return "%s %s %s.%s".formatted(
                getTaxpayerFilePrefix(),
                reportType,
                period,
                extension
        );
    }

    // ---------------------------------------------------------
    // ALERTS AND NAVIGATION
    // ---------------------------------------------------------

    private void showWarning(String message) {

        Alert alert = new Alert(
                Alert.AlertType.WARNING,
                message
        );

        alert.setHeaderText(null);
        setAlertIcon(alert);
        alert.showAndWait();
    }
    private void setAlertIcon(Alert alert) {
    	try {

            var iconStream = getClass().getResourceAsStream(
                    "/app/views/fxml/images/logo.png"
            );

            if (iconStream != null) {

                Stage stage = (Stage) alert.getDialogPane()
                        .getScene()
                        .getWindow();

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