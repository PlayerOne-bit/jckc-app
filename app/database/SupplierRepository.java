package app.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import app.models.Supplier;

public class SupplierRepository {

    private final Connection sql;

    public SupplierRepository() throws SQLException {
        sql = DatabaseConfig.getConnection();
    }
    public SupplierRepository(Connection connection) {
        sql = connection;
    }
    public int addSupplier(Supplier supplier) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(
                DATABASE.INSERT_SUPPLIER, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, supplier.getTaxId());
            ps.setString(2, supplier.getTinNum());
            ps.setString(3, supplier.getTradeName());
            ps.setString(4, supplier.getBussAddress());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    supplier.setId(keys.getInt(1));
                    return supplier.getId();
                }
            }
        }
        throw new SQLException("Failed to insert supplier, no ID obtained.");
    }
    public Supplier getOrCreateSupplier(Supplier supplier) throws SQLException {
        Supplier existing = findByTin(supplier.getTaxId(), supplier.getTinNum());
        if (existing != null) {
            return existing;
        }
        addSupplier(supplier);
        return supplier;
    }

    public List<Supplier> getSuppliersByTaxId(int taxId) throws SQLException {
        List<Supplier> list = new ArrayList<>();

        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_SUPPLIERS_BY_TAX_ID)) {
            ps.setInt(1, taxId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSupplier(rs));
                }
            }
        }
        return list;
    }

    public Supplier getSupplierById(int id) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_SUPPLIER_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapSupplier(rs);
                }
            }
        }
        return null;
    }

    public Supplier findByTin(int taxId, String tinNum) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_SUPPLIER_BY_TIN)) {
            ps.setInt(1, taxId);
            ps.setString(2, tinNum);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapSupplier(rs);
                }
            }
        }
        return null;
    }
    public boolean isSupplierUsed(int supplierId) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.EXISTS_PURCHASE_FOR_SUPPLIER)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
    public void updateSupplier(Supplier supplier) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.UPDATE_SUPPLIER)) {
            ps.setString(1, supplier.getTinNum());
            ps.setString(2, supplier.getTradeName());
            ps.setString(3, supplier.getBussAddress());
            ps.setInt(4, supplier.getId());
            ps.executeUpdate();
        }
    }
    public boolean deleteSupplier(int supplierId) throws SQLException {
        if (isSupplierUsed(supplierId)) {
            return false;
        }
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.DELETE_SUPPLIER)) {
            ps.setInt(1, supplierId);
            ps.executeUpdate();
        }
        return true;
    }
    private Supplier mapSupplier(ResultSet rs) throws SQLException {
        Supplier supplier = new Supplier();
        supplier.setId(rs.getInt(DATABASE.COLUMN_SU_ID));
        supplier.setTaxId(rs.getInt(DATABASE.COLUMN_SU_TAX_ID));
        supplier.setTinNum(rs.getString(DATABASE.COLUMN_SU_TIN_NUM));
        supplier.setTradeName(rs.getString(DATABASE.COLUMN_SU_TRADE_NAME));
        supplier.setBussAddress(rs.getString(DATABASE.COLUMN_SU_BUSS_ADDRESS));
        return supplier;
    }

    private static class DATABASE {

        static final String COLUMN_SU_ID = "id";
        static final String COLUMN_SU_TAX_ID = "taxId";
        static final String COLUMN_SU_TIN_NUM = "tinNum";
        static final String COLUMN_SU_TRADE_NAME = "tradeName";
        static final String COLUMN_SU_BUSS_ADDRESS = "bussAddress";

        static final String SELECT_SUPPLIERS_BY_TAX_ID =
                "SELECT * FROM Supplier WHERE taxId = ? ORDER BY tradeName COLLATE NOCASE";
        static final String SELECT_SUPPLIER_BY_ID =
                "SELECT * FROM Supplier WHERE id = ?";
        static final String SELECT_SUPPLIER_BY_TIN =
                "SELECT * FROM Supplier WHERE taxId = ? AND tinNum = ?";
        static final String EXISTS_PURCHASE_FOR_SUPPLIER =
                "SELECT 1 FROM Purchase WHERE supplierId = ? LIMIT 1";
        static final String INSERT_SUPPLIER =
                "INSERT INTO Supplier (taxId, tinNum, tradeName, bussAddress) " +
                "VALUES (?, ?, ?, ?)";
        static final String UPDATE_SUPPLIER =
                "UPDATE Supplier SET " +
                "tinNum = ?, " +
                "tradeName = ?, " +
                "bussAddress = ? " +
                "WHERE id = ?";
        static final String DELETE_SUPPLIER =
                "DELETE FROM Supplier WHERE id = ?";
    }
}