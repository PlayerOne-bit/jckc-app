package app.viewmodels;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import app.database.SupplierRepository;
import app.models.Supplier;
import app.models.Taxpayer;

public class SupplierViewModel {
    SupplierRepository db;
    List<Supplier> suppliers = new ArrayList<>();

    public SupplierViewModel() throws Exception {
        db = new SupplierRepository();
        refreshSuppliers();
    }

    private void refreshSuppliers() throws Exception {
        Taxpayer taxpayer = Taxpayer.getTaxpayer();
        if (taxpayer == null) {
            suppliers = new ArrayList<>();
            return;
        }
        suppliers = db.getSuppliersByTaxId(taxpayer.getId());
    }

    public boolean deleteSupplier(int id) throws Exception {
        boolean deleted = db.deleteSupplier(id);
        if (deleted) {
            refreshSuppliers();
        }
        return deleted;
    }

    public String createSupplier(Supplier supplier) throws Exception {
        Supplier existing = db.findByTin(supplier.getTaxId(), supplier.getTinNum());
        if (existing != null) {
            return "This TIN number is already registered to " + existing.getTradeName() + ".";
        }
        db.addSupplier(supplier);
        refreshSuppliers();
        return null;
    }

    public List<Supplier> loadSuppliers() {
        suppliers.sort(Comparator.comparing(
                Supplier::getTinNum, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
        return suppliers;
    }
}