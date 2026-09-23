package app.viewmodels;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import app.database.SupplierRepository;
import app.models.Supplier;
import app.models.Taxpayer;

public class SupplierViewModel {
	SupplierRepository db;
	List<Supplier> suppliers=new ArrayList<>();
	public SupplierViewModel() throws Exception{
		db = new SupplierRepository();
		refreshSuppliers();
	}
	private void refreshSuppliers()throws Exception {
		suppliers = db.getSuppliersByTaxId(Taxpayer.getTaxpayer().getId());
	}
	public void deleteSupplier(int id) throws Exception{
		db.deleteSupplier(id);
	}
	public void createSupplier(Supplier supplier) throws Exception{
		db.getOrCreateSupplier(supplier);
	}
	public List<Supplier> loadSuppliers() {
		suppliers.sort(Comparator.comparing(
				Supplier::getTinNum,Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
		return suppliers;
	}
}
