package app.viewmodels;

import java.util.ArrayList;
import java.util.List;

import app.database.SlspRepository;
import app.models.Purchase;
import app.models.SLSP;
import app.models.Sale;
import app.models.Taxpayer;

/**
 * Coordinates loading and saving a single SLSP period (header + its Sale
 * and Purchase rows) for SlspManagerController. SlspViewModel remains the
 * one used by the SLSP list page.
 */
public class SlspManagerViewModel {

    private final SlspRepository repo;
    private SLSP slsp; // null until a saved SLSP exists for the loaded period

    public SlspManagerViewModel() throws Exception {
        repo = new SlspRepository();
    }

    public boolean isExisting() {
        return slsp != null;
    }

    public SLSP getSlsp() {
        return slsp;
    }

    public List<Sale> getSales() {
        return slsp != null ? slsp.getSales() : new ArrayList<>();
    }

    public List<Purchase> getPurchases() {
        return slsp != null ? slsp.getPurchases() : new ArrayList<>();
    }

    /**
     * Loads the SLSP for this taxpayer+period, with its rows attached, if
     * one already exists. If not, getSlsp() stays null — the caller starts
     * with empty tables for a brand-new period.
     */
    public void loadForPeriod(String period) throws Exception {
        int taxId = Taxpayer.getTaxpayer().getId();
        slsp = repo.getSLSPByTaxIdAndPeriod(taxId, period);
    }

    /**
     * Creates the SLSP header if it doesn't exist yet, then atomically
     * replaces all of its Sale/Purchase rows with the given lists.
     * Returns false only if a different SLSP already owns this period
     * (collision on first-time creation).
     */
    public boolean save(String period, List<Sale> sales, List<Purchase> purchases) throws Exception {
        int taxId = Taxpayer.getTaxpayer().getId();

        if (slsp == null) {
            if (repo.slspExists(taxId, period)) {
                return false;
            }
            SLSP created = new SLSP();
            created.setTaxId(taxId);
            created.setPeriod(period);
            repo.addSLSP(created);
            slsp = created;
        } else if (!period.equals(slsp.getPeriod())) {
            // Editing and moved to a different month/year: make sure it's not taken
            if (repo.slspExists(taxId, period)) {
                return false;   // checked before mutating slsp, so the loaded state stays intact
            }
            slsp.setPeriod(period);
            repo.updateSLSP(slsp);
        }

        repo.saveSlspRows(slsp, sales, purchases);
        slsp.setSales(sales);
        slsp.setPurchases(purchases);
        return true;
    }

    public void deleteCurrentSlsp() throws Exception {
        if (slsp == null) return;
        repo.deleteSLSP(slsp.getId()); // already cascades sales + purchases
        slsp = null;
    }
}