package app.viewmodels;

import java.sql.SQLException;

import app.database.TaxpayerRepository;
import app.models.Taxpayer;

public class AppPasswordViewModel {

    private final TaxpayerRepository repo;

    public AppPasswordViewModel() throws SQLException {
        repo = new TaxpayerRepository();
    }

    /**
     * Saves the app password for this taxpayer.
     * Returns false when no taxpayer with that id exists (nothing was saved).
     */
    public boolean setAppPassword(int taxId, String appPassword) throws SQLException {
        boolean saved = repo.updateAppPassword(taxId, appPassword);
        if (saved) {
            // Taxpayer.getTaxpayer() is long-lived, so keep it in step with the database
            Taxpayer current = Taxpayer.getTaxpayer();
            if (current != null && current.getId() == taxId) {
                current.setAppPassword(appPassword);
            }
        }
        return saved;
    }
}