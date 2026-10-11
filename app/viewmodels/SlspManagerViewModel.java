package app.viewmodels;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.database.SlspRepository;
import app.database.SupplierRepository;
import app.models.Purchase;
import app.models.SLSP;
import app.models.Sale;
import app.models.Supplier;
import app.models.Taxpayer;

public class SlspManagerViewModel {

    private final SlspRepository repo;
    private final SupplierRepository supplierRepo;

    private SLSP slsp;

    // Supplier ID -> Supplier object
    private final Map<Integer, Supplier> supplierMap = new HashMap<>();

    public SlspManagerViewModel() throws Exception {
        repo = new SlspRepository();
        supplierRepo = new SupplierRepository();

        loadSuppliers();
    }

    // ==========================================
    // SUPPLIER DATA
    // ==========================================

    private void loadSuppliers() throws Exception {

        int taxId = Taxpayer.getTaxpayer().getId();

        supplierMap.clear();

        // Replace with your actual repository method.
        List<Supplier> suppliers =
                supplierRepo.getSuppliersByTaxId(taxId);

        for (Supplier supplier : suppliers) {
            supplierMap.put(supplier.getId(), supplier);
        }
    }

    public List<Supplier> getSuppliers() {
        return new ArrayList<>(supplierMap.values());
    }

    public Supplier getSupplierById(int supplierId) {
        return supplierMap.get(supplierId);
    }

    public String getSupplierName(int supplierId) {

        Supplier supplier = supplierMap.get(supplierId);

        if (supplier == null) {
            return "";
        }

        return supplier.getTradeName() != null
                ? supplier.getTradeName()
                : "";
    }

    // ==========================================
    // SLSP DATA
    // ==========================================

    public boolean isExisting() {
        return slsp != null;
    }

    public SLSP getSlsp() {
        return slsp;
    }

    public List<Sale> getSales() {
        return slsp != null
                ? slsp.getSales()
                : new ArrayList<>();
    }

    public List<Purchase> getPurchases() {
        return slsp != null
                ? slsp.getPurchases()
                : new ArrayList<>();
    }

    // ==========================================
    // LOAD PERIOD
    // ==========================================

    public void loadForPeriod(String period) throws Exception {

        int taxId = Taxpayer.getTaxpayer().getId();

        loadSuppliers();

        slsp = repo.getSLSPByTaxIdAndPeriod(taxId, period);

        if (slsp != null) {
            sortRows(slsp.getSales(), slsp.getPurchases());
        }
    }

    // ==========================================
    // SORTING
    // ==========================================

    private void sortRows(
            List<Sale> sales,
            List<Purchase> purchases
    ) {

        // SALES: Sale Date -> Sales Invoice Number
        sales.sort(
            Comparator.comparing(
                (Sale sale) -> parseDate(sale.getSaleDate()),
                Comparator.nullsLast(Comparator.naturalOrder())
            ).thenComparing(
                Sale::getSiNum,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            )
        );

        // PURCHASES: Supplier Trade Name -> Purchase Date -> Invoice Number
        purchases.sort(
            Comparator.comparing(
                (Purchase purchase) ->
                    getSupplierName(purchase.getSupplierId()),
                String.CASE_INSENSITIVE_ORDER
            ).thenComparing(
                purchase -> parseDate(purchase.getPurchaseDate()),
                Comparator.nullsLast(Comparator.naturalOrder())
            ).thenComparing(
                Purchase::getInvoiceNum,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            )
        );
    }

    private LocalDate parseDate(String date) {

        if (date == null || date.isBlank()) {
            return null;
        }

        return LocalDate.parse(date.trim());
    }

    // ==========================================
    // SAVE
    // ==========================================

    public boolean save(
            String period,
            List<Sale> sales,
            List<Purchase> purchases
    ) throws Exception {

        int taxId = Taxpayer.getTaxpayer().getId();

        if (slsp == null || !period.equals(slsp.getPeriod())) {

            if (repo.slspExists(taxId, period)) {
                return false;
            }
        }

        // Refresh suppliers before sorting.
        loadSuppliers();

        // Sort copies to avoid modifying TableView data prematurely.
        List<Sale> sortedSales = new ArrayList<>(sales);
        List<Purchase> sortedPurchases = new ArrayList<>(purchases);

        sortRows(sortedSales, sortedPurchases);

        if (slsp == null) {

            SLSP created = new SLSP();
            created.setTaxId(taxId);
            created.setPeriod(period);

            repo.addSLSP(created);
            slsp = created;

        } else if (!period.equals(slsp.getPeriod())) {

            slsp.setPeriod(period);
            repo.updateSLSP(slsp);
        }

        repo.saveSlspRows(slsp, sortedSales, sortedPurchases);

        slsp.setSales(sortedSales);
        slsp.setPurchases(sortedPurchases);

        return true;
    }

    // ==========================================
    // DELETE
    // ==========================================

    public void deleteCurrentSlsp() throws Exception {

        if (slsp == null) {
            return;
        }

        repo.deleteSLSP(slsp.getId());

        slsp = null;
    }
}