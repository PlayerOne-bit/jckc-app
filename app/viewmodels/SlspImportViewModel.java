package app.viewmodels;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import app.database.SlspRepository;
import app.database.SupplierRepository;
import app.importer.SlspExcelImporter;
import app.importer.SlspExcelImporter.ParsedMonth;
import app.importer.SlspExcelImporter.ParsedPurchase;
import app.importer.SlspExcelImporter.ParsedSale;
import app.importer.SlspExcelImporter.ParsedSupplier;
import app.models.Purchase;
import app.models.SLSP;
import app.models.Sale;
import app.models.Supplier;
import app.models.Taxpayer;

/**
 * Saves a parsed SLSP workbook for the selected taxpayer.
 *
 * "Same row" (used for both the orange preview and MERGE) means:
 *   Sale     : same date + same invoice number
 *   Purchase : same supplier + same date + same invoice number
 * Rows with no invoice number fall back to date + amounts, so importing the same file twice
 * does not double them. analyze() and save() use the same keys, so the preview matches what is saved.
 *
 * Threading: this class only touches the database; it does not use JavaFX, so it can run in a Task.
 */
public class SlspImportViewModel {

    public enum Mode {
        /**
         * Rows whose key matches an existing row replace it; every other existing row is kept and
         * every other imported row is added. Importing the same file twice leaves the data unchanged.
         */
        MERGE,
        /**
         * For every month in the file, the existing Sales (and/or Purchases) are deleted and replaced by
         * the imported ones. Only the kind the file actually contains is replaced.
         */
        REPLACE,
        /** Leave months that already have an SLSP untouched. */
        SKIP
    }

    public record MonthPlan(YearMonth period, boolean exists, int sales, int purchases,
                            int duplicateSales, int duplicatePurchases) {}

    public record Plan(List<MonthPlan> months, int newSuppliers, int existingSuppliers,
                       Set<ParsedSale> replaceSales, Set<ParsedPurchase> replacePurchases) {}

    /**
     * salesAdded / purchasesAdded    imported rows that did not replace anything (REPLACE: all imported rows)
     * duplicatesReplaced             imported rows that replaced an existing row (REPLACE: existing rows deleted)
     * outsideMonthSkipped            rows whose date is not in the month they were found under
     */
    public record Summary(int monthsCreated, int monthsUpdated, int monthsSkipped,
                          int salesAdded, int purchasesAdded, int duplicatesReplaced,
                          int outsideMonthSkipped, int suppliersCreated) {}

    private final SlspRepository slspRepo;
    private final SupplierRepository supplierRepo;
    private final Taxpayer taxpayer;

    public SlspImportViewModel() throws SQLException {
        this.slspRepo = new SlspRepository();
        this.supplierRepo = new SupplierRepository();
        this.taxpayer = Taxpayer.getTaxpayer();
        if (taxpayer == null) throw new IllegalStateException("No taxpayer is currently selected.");
    }

    // ---------------------------------------------------------
    // PLAN (read-only: what would happen)
    // ---------------------------------------------------------

    public Plan analyze(SlspExcelImporter.Result parsed) throws SQLException {
        SupplierIndex index = new SupplierIndex(supplierRepo.getSuppliersByTaxId(taxpayer.getId()));

        List<MonthPlan> plans = new ArrayList<>();
        Set<ParsedSale> replaceSales = identitySet();
        Set<ParsedPurchase> replacePurchases = identitySet();

        for (ParsedMonth m : parsed.months()) {
            SLSP existing = slspRepo.getSLSPByTaxIdAndPeriod(taxpayer.getId(), m.period().toString());
            int dupSales = 0, dupPurchases = 0;

            if (existing != null) {
                Set<String> saleKeys = new HashSet<>();
                for (Sale x : orEmpty(existing.getSales())) saleKeys.add(keyOf(x));
                Set<String> purchaseKeys = new HashSet<>();
                for (Purchase x : orEmpty(existing.getPurchases())) purchaseKeys.add(keyOf(x));

                for (ParsedSale s : m.sales()) {
                    if (saleKeys.contains(keyOf(s))) {
                        dupSales++;
                        replaceSales.add(s);
                    }
                }
                for (ParsedPurchase p : m.purchases()) {
                    Supplier supplier = index.find(p.tin(), p.tradeName());
                    if (supplier != null && purchaseKeys.contains(keyOf(supplier.getId(), p))) {
                        dupPurchases++;
                        replacePurchases.add(p);
                    }
                }
            }
            plans.add(new MonthPlan(m.period(), existing != null, m.sales().size(), m.purchases().size(),
                    dupSales, dupPurchases));
        }

        int known = 0, fresh = 0;
        Set<String> countedNew = new HashSet<>();
        for (ParsedSupplier s : parsed.suppliers()) {
            if (index.find(s.tin(), s.tradeName()) != null) known++;
            else if (countedNew.add(SupplierIndex.keyOf(s.tin(), s.tradeName()))) fresh++;
        }
        return new Plan(plans, fresh, known, replaceSales, replacePurchases);
    }

    // ---------------------------------------------------------
    // COMMIT
    // ---------------------------------------------------------

    public Summary save(SlspExcelImporter.Result parsed, Mode mode) throws SQLException {
        SupplierIndex index = new SupplierIndex(supplierRepo.getSuppliersByTaxId(taxpayer.getId()));
        int created = 0, updated = 0, skipped = 0;
        int salesAdded = 0, purchasesAdded = 0, replaced = 0, outside = 0;

        for (ParsedMonth m : parsed.months()) {
            if (m.sales().isEmpty() && m.purchases().isEmpty()) continue;   // everything was deleted in the preview

            String period = m.period().toString();
            SLSP existing = slspRepo.getSLSPByTaxIdAndPeriod(taxpayer.getId(), period);

            if (existing != null && mode == Mode.SKIP) {
                skipped++;
                continue;
            }

            // A row dated outside its month would silently move to another month when the SLSP is reopened
            List<Sale> newSales = new ArrayList<>();
            for (ParsedSale s : m.sales()) {
                if (!YearMonth.from(s.date()).equals(m.period())) { outside++; continue; }
                newSales.add(toSale(s));
            }
            List<Purchase> newPurchases = new ArrayList<>();
            for (ParsedPurchase p : m.purchases()) {
                if (!YearMonth.from(p.date()).equals(m.period())) { outside++; continue; }
                Supplier supplier = index.findOrCreate(p, supplierRepo, taxpayer.getId());
                newPurchases.add(toPurchase(p, supplier));
            }
            if (newSales.isEmpty() && newPurchases.isEmpty()) continue;

            if (existing == null) {
                SLSP slsp = new SLSP();
                slsp.setTaxId(taxpayer.getId());
                slsp.setPeriod(period);
                slspRepo.addSLSP(slsp);

                sortSales(newSales);
                sortPurchases(newPurchases);
                slspRepo.saveSlspRows(slsp, newSales, newPurchases);

                created++;
                salesAdded += newSales.size();
                purchasesAdded += newPurchases.size();
                continue;
            }

            List<Sale> finalSales;
            List<Purchase> finalPurchases;

            if (mode == Mode.REPLACE) {
                finalSales = newSales.isEmpty() ? new ArrayList<>(orEmpty(existing.getSales())) : newSales;
                finalPurchases = newPurchases.isEmpty() ? new ArrayList<>(orEmpty(existing.getPurchases())) : newPurchases;
                if (!newSales.isEmpty()) replaced += orEmpty(existing.getSales()).size();
                if (!newPurchases.isEmpty()) replaced += orEmpty(existing.getPurchases()).size();
                salesAdded += newSales.size();
                purchasesAdded += newPurchases.size();

            } else {   // MERGE
                Set<String> existingSaleKeys = new HashSet<>();
                for (Sale x : orEmpty(existing.getSales())) existingSaleKeys.add(keyOf(x));
                Set<String> importedSaleKeys = new HashSet<>();
                for (Sale x : newSales) importedSaleKeys.add(keyOf(x));

                finalSales = new ArrayList<>();
                for (Sale x : orEmpty(existing.getSales())) {
                    if (!importedSaleKeys.contains(keyOf(x))) finalSales.add(x);   // keep what the file does not touch
                }
                for (Sale x : newSales) {
                    if (existingSaleKeys.contains(keyOf(x))) replaced++; else salesAdded++;
                    finalSales.add(x);
                }

                Set<String> existingPurchaseKeys = new HashSet<>();
                for (Purchase x : orEmpty(existing.getPurchases())) existingPurchaseKeys.add(keyOf(x));
                Set<String> importedPurchaseKeys = new HashSet<>();
                for (Purchase x : newPurchases) importedPurchaseKeys.add(keyOf(x));

                finalPurchases = new ArrayList<>();
                for (Purchase x : orEmpty(existing.getPurchases())) {
                    if (!importedPurchaseKeys.contains(keyOf(x))) finalPurchases.add(x);
                }
                for (Purchase x : newPurchases) {
                    if (existingPurchaseKeys.contains(keyOf(x))) replaced++; else purchasesAdded++;
                    finalPurchases.add(x);
                }
            }

            sortSales(finalSales);
            sortPurchases(finalPurchases);
            slspRepo.saveSlspRows(existing, finalSales, finalPurchases);
            updated++;
        }

        return new Summary(created, updated, skipped, salesAdded, purchasesAdded, replaced,
                outside, index.created());
    }

    // ---------------------------------------------------------
    // MODEL CONVERSION AND ORDER
    // ---------------------------------------------------------

    private static Sale toSale(ParsedSale s) {
        Sale sale = new Sale();
        sale.setSaleDate(s.date().toString());
        sale.setSiNum(s.invoiceNo());
        sale.setExemptAmount(s.exempt());
        sale.setZeroRatedAmount(s.zeroRated());
        sale.setGrossAmount(s.gross());
        return sale;
    }

    private static Purchase toPurchase(ParsedPurchase p, Supplier supplier) {
        Purchase purchase = new Purchase();
        purchase.setSupplierId(supplier.getId());
        purchase.setPurchaseDate(p.date().toString());
        purchase.setInvoiceNum(p.invoiceNo());
        purchase.setExemptAmount(p.exempt());
        purchase.setZeroRatedAmount(p.zeroRated());
        purchase.setGrossAmount(p.gross());
        return purchase;
    }

    /** Dates are ISO text (yyyy-MM-dd), so text order is date order. */
    private static void sortSales(List<Sale> sales) {
        sales.sort(Comparator
                .comparing(Sale::getSaleDate, Comparator.nullsFirst(Comparator.<String>naturalOrder()))
                .thenComparing(x -> norm(x.getSiNum())));
    }

    private static void sortPurchases(List<Purchase> purchases) {
        purchases.sort(Comparator
                .comparing(Purchase::getPurchaseDate, Comparator.nullsFirst(Comparator.<String>naturalOrder()))
                .thenComparing(x -> norm(x.getInvoiceNum())));
    }

    // ---------------------------------------------------------
    // "SAME ROW" KEYS (shared by analyze and save)
    // ---------------------------------------------------------

    private static String norm(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** The invoice number, or (when blank) the amounts, so blank-invoice rows still match themselves. */
    private static String rowId(String invoice, BigDecimal exempt, BigDecimal zero, BigDecimal gross) {
        String inv = norm(invoice);
        return inv.isEmpty() ? "#" + money(exempt) + "|" + money(zero) + "|" + money(gross) : inv;
    }

    private static String keyOf(ParsedSale s) {
        return s.date() + "|" + rowId(s.invoiceNo(), s.exempt(), s.zeroRated(), s.gross());
    }

    private static String keyOf(Sale s) {
        return s.getSaleDate() + "|" + rowId(s.getSiNum(), s.getExemptAmount(), s.getZeroRatedAmount(),
                s.getGrossAmount());
    }

    private static String keyOf(int supplierId, ParsedPurchase p) {
        return supplierId + "|" + p.date() + "|" + rowId(p.invoiceNo(), p.exempt(), p.zeroRated(), p.gross());
    }

    private static String keyOf(Purchase p) {
        return p.getSupplierId() + "|" + p.getPurchaseDate() + "|"
                + rowId(p.getInvoiceNum(), p.getExemptAmount(), p.getZeroRatedAmount(), p.getGrossAmount());
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static <T> Set<T> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    // ---------------------------------------------------------
    // SUPPLIER MATCHING
    // ---------------------------------------------------------

    /** Finds a taxpayer's existing supplier by TIN (any punctuation), or by name when the file has no TIN. */
    private static final class SupplierIndex {
        private final Map<String, Supplier> byTin = new HashMap<>();
        private final Map<String, Supplier> byName = new HashMap<>();
        private int created = 0;

        SupplierIndex(List<Supplier> existing) {
            for (Supplier s : existing) put(s);
        }

        int created() { return created; }

        /** Letters and digits only; a 9-digit TIN is the same as the same number ending in 000. */
        static String tinKey(String tin) {
            String key = tin == null ? "" : tin.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
            return key.matches("\\d{9}") ? key + "000" : key;
        }

        static String keyOf(String tin, String name) {
            String t = tinKey(tin);
            return t.isEmpty() ? "n:" + norm(name) : "t:" + t;
        }

        /** Stored like the Supplier screen does it: 123-456-789-000. Anything unusual is kept as typed. */
        static String formatTin(String raw) {
            String t = raw == null ? "" : raw.trim();
            if (!t.matches("[0-9\\s.\\-]+")) return t;
            String d = t.replaceAll("[^0-9]", "");
            if (d.length() == 9) d += "000";
            if (d.length() != 12) return t;
            return d.substring(0, 3) + "-" + d.substring(3, 6) + "-" + d.substring(6, 9) + "-" + d.substring(9);
        }

        private void put(Supplier s) {
            String t = tinKey(s.getTinNum());
            if (!t.isEmpty()) byTin.putIfAbsent(t, s);
            String name = norm(s.getTradeName());
            if (!name.isEmpty()) byName.putIfAbsent(name, s);
        }

        Supplier find(String tin, String name) {
            String t = tinKey(tin);
            if (!t.isEmpty()) return byTin.get(t);
            String n = norm(name);
            return n.isEmpty() ? null : byName.get(n);
        }

        Supplier findOrCreate(ParsedPurchase p, SupplierRepository repo, int taxId) throws SQLException {
            Supplier found = find(p.tin(), p.tradeName());
            if (found != null) return found;

            Supplier s = new Supplier();
            s.setTaxId(taxId);
            s.setTinNum(formatTin(p.tin()));
            s.setTradeName(p.tradeName());
            s.setBussAddress(p.address());
            repo.addSupplier(s);
            put(s);
            created++;
            return s;
        }
    }
}