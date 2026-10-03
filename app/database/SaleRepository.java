package app.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import app.models.Sale;

public class SaleRepository {

    private final Connection sql;

    public SaleRepository() throws SQLException {
        sql = DatabaseConfig.getConnection();
    }

    public SaleRepository(Connection connection) {
        sql = connection;
    }

    public int addSale(Sale sale) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(
                DATABASE.INSERT_SALE, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, sale.getSlspId());
            ps.setString(2, sale.getSaleDate());
            ps.setString(3, sale.getSiNum());
            ps.setBigDecimal(4, sale.getExemptAmount());
            ps.setBigDecimal(5, sale.getZeroRatedAmount());
            ps.setBigDecimal(6, sale.getTaxableAmount());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    sale.setId(keys.getInt(1));
                    return sale.getId();
                }
            }
        }
        throw new SQLException("Failed to insert sale, no ID obtained.");
    }

    public List<Sale> getSalesBySlspId(int slspId) throws SQLException {
        List<Sale> list = new ArrayList<>();

        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_SALES_BY_SLSP_ID)) {
            ps.setInt(1, slspId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSale(rs));
                }
            }
        }
        return list;
    }

    public Sale getSaleById(int id) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_SALE_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapSale(rs);
                }
            }
        }
        return null;
    }

    public void updateSale(Sale sale) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.UPDATE_SALE)) {
            ps.setString(1, sale.getSaleDate());
            ps.setString(2, sale.getSiNum());
            ps.setBigDecimal(3, sale.getExemptAmount());
            ps.setBigDecimal(4, sale.getZeroRatedAmount());
            ps.setBigDecimal(5, sale.getTaxableAmount());
            ps.setInt(6, sale.getId());
            ps.executeUpdate();
        }
    }

    public void deleteSale(int saleId) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.DELETE_SALE)) {
            ps.setInt(1, saleId);
            ps.executeUpdate();
        }
    }

    public void deleteSalesBySlspId(int slspId) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.DELETE_SALES_BY_SLSP_ID)) {
            ps.setInt(1, slspId);
            ps.executeUpdate();
        }
    }

    private Sale mapSale(ResultSet rs) throws SQLException {
        Sale sale = new Sale();
        sale.setId(rs.getInt(DATABASE.COLUMN_ID));
        sale.setSlspId(rs.getInt(DATABASE.COLUMN_SLSP_ID));
        sale.setSaleDate(rs.getString(DATABASE.COLUMN_SALE_DATE));
        sale.setSiNum(rs.getString(DATABASE.COLUMN_SI_NUM));
        sale.setExemptAmount(rs.getBigDecimal(DATABASE.COLUMN_EXEMPT));
        sale.setZeroRatedAmount(rs.getBigDecimal(DATABASE.COLUMN_ZERO_RATED));
        sale.setTaxableAmount(rs.getBigDecimal(DATABASE.COLUMN_TAXABLE));
        return sale;
    }

    private static class DATABASE {

        // ASSUMPTION: table name is "Sale" and columns match the model's field names.
        // Adjust these to match your actual schema if different.
        static final String COLUMN_ID = "id";
        static final String COLUMN_SLSP_ID = "slspId";
        static final String COLUMN_SALE_DATE = "saleDate";
        static final String COLUMN_SI_NUM = "siNum";
        static final String COLUMN_EXEMPT = "exemptAmount";
        static final String COLUMN_ZERO_RATED = "zeroRatedAmount";
        static final String COLUMN_TAXABLE = "taxableAmount";

        static final String SELECT_SALES_BY_SLSP_ID =
                "SELECT * FROM Sale WHERE slspId = ? ORDER BY saleDate";
        static final String SELECT_SALE_BY_ID =
                "SELECT * FROM Sale WHERE id = ?";
        static final String INSERT_SALE =
                "INSERT INTO Sale (slspId, saleDate, siNum, exemptAmount, zeroRatedAmount, taxableAmount) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        static final String UPDATE_SALE =
                "UPDATE Sale SET " +
                "saleDate = ?, " +
                "siNum = ?, " +
                "exemptAmount = ?, " +
                "zeroRatedAmount = ?, " +
                "taxableAmount = ? " +
                "WHERE id = ?";
        static final String DELETE_SALE =
                "DELETE FROM Sale WHERE id = ?";
        static final String DELETE_SALES_BY_SLSP_ID =
                "DELETE FROM Sale WHERE slspId = ?";
    }
}