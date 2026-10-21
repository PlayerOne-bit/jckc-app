package app.viewmodels;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import app.models.Purchase;
import app.models.SLSP;
import app.models.Sale;
import app.models.Supplier;
import app.models.Taxpayer;

/** Saves a parsed SLSP workbook for the selected taxpayer. */
public class SlspImportViewModel {

    public enum Mode {
        /** Add only rows that are not already in the month (same date, invoice and amount). */
        MERGE,
        /** Delete the month's existing sales and purchases, then save the imported ones. */
        REPLACE,
        /** Leave months that already have an SLSP untouched. */
        SKIP
    }

    public record MonthPlan(YearMonth period, boolean exists, int sales, int purchases,
                            int duplicateSales, int duplicatePurchases) {}

    public record Plan(List<MonthPlan> months, int newSuppliers, int existingSuppliers) {}

    public record Summary(int monthsCreated, int monthsUpdated, int monthsSkipped,
                          int salesAdded, int purchasesAdded, int duplicatesSkipped,
                          int suppliersCreated) {}

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

        for (ParsedMonth m : parsed.months()) {
            SLSP existing = slspRepo.getSLSPByTaxIdAndPeriod(taxpayer.getId(), m.period().toString());
            int dupSales = 0, dupPurchases = 0;

            if (existing != null) {
                Set<String> saleKeys = saleKeys(existing.getSales());
                Set<String> purchaseKeys = purchaseKeys(existing.getPurchases());
                for (ParsedSale s : m.sales()) {
                    if (saleKeys.contains(saleKey(s.date().toString(), s.invoiceNo(), s.gross()))) dupSales++;
                }
                for (ParsedPurchase p : m.purchases()) {
                    Supplier sup = index.find(p.tin(), p.tradeName());
                    if (sup != null && purchaseKeys.contains(
                            purchaseKey(sup.getId(), p.date().toString(), p.invoiceNo(), p.gross()))) {
                        dupPurchases++;
                    }
                }
            }
            plans.add(new MonthPlan(m.period(), existing != null, m.sales().size(), m.purchases().size(),
                    dupSales, dupPurchases));
        }

        int known = 0, fresh = 0;
        for (SlspExcelImporter.ParsedSupplier s : parsed.suppliers()) {
            if (index.find(s.tin(), s.tradeName()) != null) known++; else fresh++;
        }
        return new Plan(plans, fresh, known);
    }

    // ---------------------------------------------------------
    // COMMIT
    // ---------------------------------------------------------

    public Summary save(SlspExcelImporter.Result parsed, Mode mode) throws SQLException {
        SupplierIndex index = new SupplierIndex(supplierRepo.getSuppliersByTaxId(taxpayer.getId()));
        int created = 0, updated = 0, skipped = 0, salesAdded = 0, purchasesAdded = 0, dupes = 0;
        int suppliersBefore = index.size();

        for (ParsedMonth m : parsed.months()) {
            if (m.sales().isEmpty() && m.purchases().isEmpty()) continue;

            String period = m.period().toString();
            SLSP existing = slspRepo.getSLSPByTaxIdAndPeriod(taxpayer.getId(), period);

            if (existing != null && mode == Mode.SKIP) {
                skipped++;
                continue;
            }

            List<Sale> newSales = new ArrayList<>();
            for (ParsedSale s : m.sales()) {
                Sale sale = new Sale();
                sale.setSaleDate(s.date().toString());
                sale.setSiNum(s.invoiceNo());
                sale.setExemptAmount(s.exempt());
                sale.setZeroRatedAmount(s.zeroRated());
                sale.setGrossAmount(s.gross());
                newSales.add(sale);
            }

            List<Purchase> newPurchases = new ArrayList<>();
            for (ParsedPurchase p : m.purchases()) {
                Supplier sup = index.findOrCreate(p, supplierRepo, taxpayer.getId());
                Purchase purchase = new Purchase();
                purchase.setSupplierId(sup.getId());
                purchase.setPurchaseDate(p.date().toString());
                purchase.setInvoiceNum(p.invoiceNo());
                purchase.setExemptAmount(p.exempt());
                purchase.setZeroRatedAmount(p.zeroRated());
                purchase.setGrossAmount(p.gross());
                newPurchases.add(purchase);
            }

            if (existing == null) {
                SLSP slsp = new SLSP();
                slsp.setTaxId(taxpayer.getId());
                slsp.setPeriod(period);
                slspRepo.addSLSP(slsp);
                slspRepo.saveSlspRows(slsp, newSales, newPurchases);
                created++;
                salesAdded += newSales.size();
                purchasesAdded += newPurchases.size();

            } else if (mode == Mode.REPLACE) {
                slspRepo.saveSlspRows(existing, newSales, newPurchases);
                updated++;
                salesAdded += newSales.size();
                purchasesAdded += newPurchases.size();

            } else {   // MERGE
                Set<String> saleKeys = saleKeys(existing.getSales());
                Set<String> purchaseKeys = purchaseKeys(existing.getPurchases());

                List<Sale> allSales = new ArrayList<>(existing.getSales());
                for (Sale s : newSales) {
                    if (saleKeys.contains(saleKey(s.getSaleDate(), s.getSiNum(), s.getGrossAmount()))) dupes++;
                    else { allSales.add(s); salesAdded++; }
                }
                List<Purchase> allPurchases = new ArrayList<>(existing.getPurchases());
                for (Purchase p : newPurchases) {
                    if (purchaseKeys.contains(purchaseKey(p.getSupplierId(), p.getPurchaseDate(),
                            p.getInvoiceNum(), p.getGrossAmount()))) dupes++;
                    else { allPurchases.add(p); purchasesAdded++; }
                }
                slspRepo.saveSlspRows(existing, allSales, allPurchases);
                updated++;
            }
        }
        return new Summary(created, updated, skipped, salesAdded, purchasesAdded, dupes,
                index.size() - suppliersBefore);
    }

    // ---------------------------------------------------------
    // DUPLICATE KEYS
    // ---------------------------------------------------------

    private static String n(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }

    private static String money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String saleKey(String date, String si, BigDecimal gross) {
        return date + "|" + n(si) + "|" + money(gross);
    }

    private static String purchaseKey(int supplierId, String date, String invoice, BigDecimal gross) {
        return supplierId + "|" + date + "|" + n(invoice) + "|" + money(gross);
    }

    private static Set<String> saleKeys(List<Sale> sales) {
        Set<String> keys = new HashSet<>();
        if (sales != null) for (Sale s : sales) keys.add(saleKey(s.getSaleDate(), s.getSiNum(), s.getGrossAmount()));
        return keys;
    }

    private static Set<String> purchaseKeys(List<Purchase> purchases) {
        Set<String> keys = new HashSet<>();
        if (purchases != null) for (Purchase p : purchases) {
            keys.add(purchaseKey(p.getSupplierId(), p.getPurchaseDate(), p.getInvoiceNum(), p.getGrossAmount()));
        }
        return keys;
    }

    // ---------------------------------------------------------
    // SUPPLIER MATCHING
    // ---------------------------------------------------------

    private static final class SupplierIndex {
        private final Map<String, Supplier> byTin = new HashMap<>();
        private final Map<String, Supplier> byName = new HashMap<>();

        SupplierIndex(List<Supplier> existing) {
            for (Supplier s : existing) put(s);
        }

        int size() { return byTin.size() + byName.size() - bothCount(); }

        private int bothCount() {
            int c = 0;
            for (Supplier s : byTin.values()) if (byName.get(n(s.getTradeName())) == s) c++;
            return c;
        }

        private static String tinKey(String tin) {
            return tin == null ? "" : tin.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        }

        private void put(Supplier s) {
            String t = tinKey(s.getTinNum());
            if (!t.isEmpty()) byTin.putIfAbsent(t, s);
            String name = n(s.getTradeName());
            if (!name.isEmpty()) byName.putIfAbsent(name, s);
        }

        Supplier find(String tin, String name) {
            String t = tinKey(tin);
            if (!t.isEmpty()) {
                Supplier s = byTin.get(t);
                if (s != null) return s;
            }
            return t.isEmpty() ? byName.get(n(name)) : null;   // name match only when the file has no TIN
        }

        Supplier findOrCreate(ParsedPurchase p, SupplierRepository repo, int taxId) throws SQLException {
            Supplier found = find(p.tin(), p.tradeName());
            if (found != null) return found;

            Supplier s = new Supplier();
            s.setTaxId(taxId);
            s.setTinNum(p.tin());
            s.setTradeName(p.tradeName());
            s.setBussAddress(p.address());
            repo.addSupplier(s);
            put(s);
            return s;
        }
    }
}