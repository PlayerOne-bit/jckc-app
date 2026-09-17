package app.viewmodels;

import java.util.List;

import app.database.Repository;
import app.models.Supplier;

public class SupplierViewModel {
	private Repository repo;
	private List<Supplier> suppliers;
	public void initialize() throws Exception{
		repo.getAllSuppliers();
	}
	public SupplierViewModel() throws Exception{
		repo=new Repository();
	}
	public void createSupplier(Supplier supplier)throws Exception {
		repo.addSupplier(supplier);
	}
	public List<Supplier> loadSuppliers() throws Exception{
		return suppliers;
	}
	public Supplier getSupplier(int id) throws Exception{
		return repo.getSupplierById(id);
	}
	public void updateSupplier(Supplier supplier)throws Exception{
		repo.updateSupplier(supplier);
	}
	public void deleteSupplier(int id)throws Exception{
		repo.deleteSupplier(id);
	}
}
