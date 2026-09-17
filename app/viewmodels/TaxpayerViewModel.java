package app.viewmodels;

import java.util.List;
import app.database.Repository;
import app.models.Taxpayer;

public class TaxpayerViewModel {
    
    private final Repository repo;
    private List<Taxpayer> taxpayers;

    public TaxpayerViewModel() throws Exception {
        this.repo = new Repository();
        refreshTaxpayers(); 
    }
    public void refreshTaxpayers() throws Exception {
        this.taxpayers = repo.getAllTaxpayers();
    }
    public List<Taxpayer> loadTaxpayers() {
        return taxpayers;
    }

    public void createTaxpayer(Taxpayer taxpayer) throws Exception {
        repo.addTaxpayer(taxpayer);
        refreshTaxpayers();
    }

    public void updateTaxpayer(Taxpayer taxpayer) throws Exception {
        repo.updateTaxpayer(taxpayer);
        refreshTaxpayers();
    }

    public void deleteTaxpayer(int id) throws Exception {
        repo.deleteTaxpayer(id);
        refreshTaxpayers();
    }

    public Taxpayer getTaxpayer(int id) throws Exception {
        return repo.getTaxpayerById(id);
    }
}
