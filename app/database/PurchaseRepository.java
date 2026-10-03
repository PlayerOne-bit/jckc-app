package app.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import app.models.Purchase;

public class PurchaseRepository {

    private final Connection sql;

    public PurchaseRepository() throws SQLException {
        sql = DatabaseConfig.getConnection();
    }

    public PurchaseRepository(Connection connection) {
        sql = connection;
    }

    public int addPurchase(Purchase purchase) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(
                DATABASE.INSERT_PURCHASE, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, purchase.getSlspId());
            ps.setInt(2, purchase.getSupplierId());
            ps.setString(3, purchase.getPurchaseDate());
            ps.setString(4, purchase.getInvoiceNum());
            ps.setBigDecimal(5, purchase.getExemptAmount());
            ps.setBigDecimal(6, purchase.getZeroRatedAmount());
            ps.setBigDecimal(7, purchase.getTaxableAmount());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    purchase.setId(keys.getInt(1));
                    return purchase.getId();
                }
            }
        }
        throw new SQLException("Failed to insert purchase, no ID obtained.");
    }

    public List<Purchase> getPurchasesBySlspId(int slspId) throws SQLException {
        List<Purchase> list = new ArrayList<>();

        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_PURCHASES_BY_SLSP_ID)) {
            ps.setInt(1, slspId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapPurchase(rs));
                }
            }
        }
        return list;
    }

    public List<Purchase> getPurchasesBySupplierId(int supplierId) throws SQLException {
        List<Purchase> list = new ArrayList<>();

        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_PURCHASES_BY_SUPPLIER_ID)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapPurchase(rs));
                }
            }
        }
        return list;
    }

    public Purchase getPurchaseById(int id) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_PURCHASE_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapPurchase(rs);
                }
            }
        }
        return null;
    }

    public void updatePurchase(Purchase purchase) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.UPDATE_PURCHASE)) {
            ps.setInt(1, purchase.getSupplierId());
            ps.setString(2, purchase.getPurchaseDate());
            ps.setString(3, purchase.getInvoiceNum());
            ps.setBigDecimal(4, purchase.getExemptAmount());
            ps.setBigDecimal(5, purchase.getZeroRatedAmount());
            ps.setBigDecimal(6, purchase.getTaxableAmount());
            ps.setInt(7, purchase.getId());
            ps.executeUpdate();
        }
    }

    public void deletePurchase(int purchaseId) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.DELETE_PURCHASE)) {
            ps.setInt(1, purchaseId);
            ps.executeUpdate();
        }
    }

    public void deletePurchasesBySlspId(int slspId) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.DELETE_PURCHASES_BY_SLSP_ID)) {
            ps.setInt(1, slspId);
            ps.executeUpdate();
        }
    }

    private Purchase mapPurchase(ResultSet rs) throws SQLException {
        Purchase purchase = new Purchase();
        purchase.setId(rs.getInt(DATABASE.COLUMN_ID));
        purchase.setSlspId(rs.getInt(DATABASE.COLUMN_SLSP_ID));
        purchase.setSupplierId(rs.getInt(DATABASE.COLUMN_SUPPLIER_ID));
        purchase.setPurchaseDate(rs.getString(DATABASE.COLUMN_PURCHASE_DATE));
        purchase.setInvoiceNum(rs.getString(DATABASE.COLUMN_INVOICE_NUM));
        purchase.setExemptAmount(rs.getBigDecimal(DATABASE.COLUMN_EXEMPT));
        purchase.setZeroRatedAmount(rs.getBigDecimal(DATABASE.COLUMN_ZERO_RATED));
        purchase.setTaxableAmount(rs.getBigDecimal(DATABASE.COLUMN_TAXABLE));
        return purchase;
    }

    private static class DATABASE {

        // ASSUMPTION: table name is "Purchase" and columns match the model's field names.
        // Adjust these to match your actual schema if different.
        static final String COLUMN_ID = "id";
        static final String COLUMN_SLSP_ID = "slspId";
        static final String COLUMN_SUPPLIER_ID = "supplierId";
        static final String COLUMN_PURCHASE_DATE = "purchaseDate";
        static final String COLUMN_INVOICE_NUM = "invoiceNum";
        static final String COLUMN_EXEMPT = "exemptAmount";
        static final String COLUMN_ZERO_RATED = "zeroRatedAmount";
        static final String COLUMN_TAXABLE = "taxableAmount";

        static final String SELECT_PURCHASES_BY_SLSP_ID =
                "SELECT * FROM Purchase WHERE slspId = ? ORDER BY purchaseDate";
        static final String SELECT_PURCHASES_BY_SUPPLIER_ID =
                "SELECT * FROM Purchase WHERE supplierId = ? ORDER BY purchaseDate";
        static final String SELECT_PURCHASE_BY_ID =
                "SELECT * FROM Purchase WHERE id = ?";
        static final String INSERT_PURCHASE =
                "INSERT INTO Purchase (slspId, supplierId, purchaseDate, invoiceNum, exemptAmount, zeroRatedAmount, taxableAmount) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        static final String UPDATE_PURCHASE =
                "UPDATE Purchase SET " +
                "supplierId = ?, " +
                "purchaseDate = ?, " +
                "invoiceNum = ?, " +
                "exemptAmount = ?, " +
                "zeroRatedAmount = ?, " +
                "taxableAmount = ? " +
                "WHERE id = ?";
        static final String DELETE_PURCHASE =
                "DELETE FROM Purchase WHERE id = ?";
        static final String DELETE_PURCHASES_BY_SLSP_ID =
                "DELETE FROM Purchase WHERE slspId = ?";
    }
}