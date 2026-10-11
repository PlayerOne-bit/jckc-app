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

    // true = deleted, false = blocked because it's used by a Purchase
    public boolean deleteSupplier(int id) throws Exception {
        boolean deleted = db.deleteSupplier(id);
        if (deleted) {
            refreshSuppliers();
        }
        return deleted;
    }
    public void editSupplier(Supplier supplier) throws Exception{
    	db.updateSupplier(supplier);
    }
    // returns null if creation succeeded, or an error message if the TIN is already taken
    public String createSupplier(Supplier supplier) throws Exception {
        Supplier existing = db.findByTin(supplier.getTaxId(), supplier.getTinNum());
        if (existing != null) {
            return "This TIN number is already registered to " + existing.getTradeName() + ".";
        }
        db.addSupplier(supplier);
        refreshSuppliers();
        return null;
    }
    /** Creates the supplier if its TIN is new, otherwise updates the one that owns that TIN.
     *  Returns the same instance you passed in when it was created, or the existing one when edited. */
    public Supplier saveSupplier(Supplier supplier) throws Exception {
        Supplier existing = db.findByTin(supplier.getTaxId(), supplier.getTinNum());
        if (existing == null) {
            db.addSupplier(supplier);
            refreshSuppliers();
            return supplier;
        }
        existing.setTradeName(supplier.getTradeName());
        existing.setBussAddress(supplier.getBussAddress());
        db.updateSupplier(existing);
        refreshSuppliers();
        return existing;
    }
    public List<Supplier> loadSuppliers() {
        suppliers.sort(Comparator.comparing(
                Supplier::getTinNum, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
        return suppliers;
    }
}