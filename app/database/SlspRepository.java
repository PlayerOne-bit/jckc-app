package app.database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import app.models.Purchase;
import app.models.Sale;
import app.models.SLSP;

public class SlspRepository {

    private final Connection sql;

    public SlspRepository() throws SQLException {
        sql = DatabaseConfig.getConnection();
    }


    public int addSLSP(SLSP slsp) throws SQLException {
        String now = now();
        slsp.setDateCreated(now);
        slsp.setDateModified(now);

        try (PreparedStatement ps = sql.prepareStatement(
                DATABASE.INSERT_SLSP, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, slsp.getTaxId());
            ps.setString(2, slsp.getPeriod());
            ps.setString(3, now);
            ps.setString(4, now);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    slsp.setId(keys.getInt(1));
                    return slsp.getId();
                }
            }
        }
        throw new SQLException("Failed to insert SLSP, no ID obtained.");
    }

    public List<SLSP> getAllSLSPs() throws SQLException {
        List<SLSP> list = new ArrayList<>();

        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_ALL_SLSP);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapSLSP(rs));
            }
        }
        for (SLSP slsp : list) {
            attachRows(slsp);
        }
        return list;
    }

    public List<SLSP> getSLSPsByTaxId(int taxId) throws SQLException {
        List<SLSP> list = new ArrayList<>();

        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_SLSP_BY_TAX_ID)) {
            ps.setInt(1, taxId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSLSP(rs));
                }
            }
        }
        for (SLSP slsp : list) {
            attachRows(slsp);
        }
        return list;
    }

    /**
     * For printing a quarter or a whole year: every SLSP of the taxpayer whose
     * period is between the two months (inclusive), oldest month first.
     * Example: getSLSPsByTaxIdBetween(id, "2026-07", "2026-09") for Q3 2026.
     */
    public List<SLSP> getSLSPsByTaxIdBetween(int taxId, String fromPeriod, String toPeriod)
            throws SQLException {
        List<SLSP> list = new ArrayList<>();

        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_SLSP_BY_TAX_ID_BETWEEN)) {
            ps.setInt(1, taxId);
            ps.setString(2, fromPeriod);
            ps.setString(3, toPeriod);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSLSP(rs));
                }
            }
        }
        for (SLSP slsp : list) {
            attachRows(slsp);
        }
        return list;
    }

    public SLSP getSLSPById(int id) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_SLSP_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    SLSP slsp = mapSLSP(rs);
                    attachRows(slsp);
                    return slsp;
                }
            }
        }
        return null;
    }

    /** Use before insert to show "this client already has an SLSP for this month". */
    public boolean slspExists(int taxId, String period) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.EXISTS_SLSP)) {
            ps.setInt(1, taxId);
            ps.setString(2, period);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void updateSLSP(SLSP slsp) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.UPDATE_SLSP)) {
            ps.setString(1, slsp.getPeriod());
            ps.setString(2, now());
            ps.setInt(3, slsp.getId());
            ps.executeUpdate();
        }
    }

    public void deleteSLSP(int slspId) throws SQLException {
        try {
            sql.setAutoCommit(false);

            executeDelete(DATABASE.DELETE_SALES_BY_SLSP_ID, slspId);
            executeDelete(DATABASE.DELETE_PURCHASES_BY_SLSP_ID, slspId);
            executeDelete(DATABASE.DELETE_SLSP, slspId);

            sql.commit();

        } catch (SQLException e) {
            sql.rollback();
            throw e;

        } finally {
            sql.setAutoCommit(true);
        }
    }

    public List<Sale> getSalesBySLSPId(int slspId) throws SQLException {
        List<Sale> sales = new ArrayList<>();

        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_SALES_BY_SLSP_ID)) {
            ps.setInt(1, slspId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.add(mapSale(rs));
                }
            }
        }
        return sales;
    }

    public int addSale(Sale sale) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(
                DATABASE.INSERT_SALE, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, sale.getSlspId());
            ps.setString(2, sale.getSiNum());
            ps.setString(3, sale.getSaleDate());
            ps.setString(4, fromAmount(sale.getExemptAmount()));
            ps.setString(5, fromAmount(sale.getZeroRatedAmount()));
            ps.setString(6, fromAmount(sale.getTaxableAmount()));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    sale.setId(keys.getInt(1));
                    touchSLSP(sale.getSlspId());
                    return sale.getId();
                }
            }
        }
        throw new SQLException("Failed to insert sale, no ID obtained.");
    }

    public void updateSale(Sale sale) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.UPDATE_SALE)) {
            ps.setString(1, sale.getSiNum());
            ps.setString(2, sale.getSaleDate());
            ps.setString(3, fromAmount(sale.getExemptAmount()));
            ps.setString(4, fromAmount(sale.getZeroRatedAmount()));
            ps.setString(5, fromAmount(sale.getTaxableAmount()));
            ps.setInt(6, sale.getId());
            ps.executeUpdate();
        }
        touchSLSP(sale.getSlspId());
    }

    public void deleteSale(int saleId, int slspId) throws SQLException {
        executeDelete(DATABASE.DELETE_SALE, saleId);
        touchSLSP(slspId);
    }


    public List<Purchase> getPurchasesBySLSPId(int slspId) throws SQLException {
        List<Purchase> purchases = new ArrayList<>();

        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_PURCHASES_BY_SLSP_ID)) {
            ps.setInt(1, slspId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    purchases.add(mapPurchase(rs));
                }
            }
        }
        return purchases;
    }

    public int addPurchase(Purchase purchase) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(
                DATABASE.INSERT_PURCHASE, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, purchase.getSlspId());
            ps.setInt(2, purchase.getSupplierId());
            ps.setString(3, purchase.getInvoiceNum());
            ps.setString(4, purchase.getPurchaseDate());
            ps.setString(5, fromAmount(purchase.getExemptAmount()));
            ps.setString(6, fromAmount(purchase.getZeroRatedAmount()));
            ps.setString(7, fromAmount(purchase.getTaxableAmount()));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    purchase.setId(keys.getInt(1));
                    touchSLSP(purchase.getSlspId());
                    return purchase.getId();
                }
            }
        }
        throw new SQLException("Failed to insert purchase, no ID obtained.");
    }

    public void updatePurchase(Purchase purchase) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.UPDATE_PURCHASE)) {
            ps.setInt(1, purchase.getSupplierId());
            ps.setString(2, purchase.getInvoiceNum());
            ps.setString(3, purchase.getPurchaseDate());
            ps.setString(4, fromAmount(purchase.getExemptAmount()));
            ps.setString(5, fromAmount(purchase.getZeroRatedAmount()));
            ps.setString(6, fromAmount(purchase.getTaxableAmount()));
            ps.setInt(7, purchase.getId());
            ps.executeUpdate();
        }
        touchSLSP(purchase.getSlspId());
    }

    public void deletePurchase(int purchaseId, int slspId) throws SQLException {
        executeDelete(DATABASE.DELETE_PURCHASE, purchaseId);
        touchSLSP(slspId);
    }

    private void attachRows(SLSP slsp) throws SQLException {
        slsp.setSales(getSalesBySLSPId(slsp.getId()));
        slsp.setPurchases(getPurchasesBySLSPId(slsp.getId()));
    }

    private void touchSLSP(int slspId) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.UPDATE_SLSP_MODIFIED)) {
            ps.setString(1, now());
            ps.setInt(2, slspId);
            ps.executeUpdate();
        }
    }

    private void executeDelete(String query, int id) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(query)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private static String now() {
        return LocalDateTime.now().withNano(0).toString();
    }

    private static String fromAmount(BigDecimal amount) {
        return (amount != null ? amount : BigDecimal.ZERO).toPlainString();
    }

    private static BigDecimal toAmount(String text) {
        return (text == null || text.isBlank()) ? BigDecimal.ZERO : new BigDecimal(text);
    }

    private SLSP mapSLSP(ResultSet rs) throws SQLException {
        SLSP slsp = new SLSP();
        slsp.setId(rs.getInt(DATABASE.COLUMN_S_ID));
        slsp.setTaxId(rs.getInt(DATABASE.COLUMN_S_TAX_ID));
        slsp.setPeriod(rs.getString(DATABASE.COLUMN_S_PERIOD));
        slsp.setDateCreated(rs.getString(DATABASE.COLUMN_S_DATE_CREATED));
        slsp.setDateModified(rs.getString(DATABASE.COLUMN_S_DATE_MODIFIED));
        return slsp;
    }

    private Sale mapSale(ResultSet rs) throws SQLException {
        Sale sale = new Sale();
        sale.setId(rs.getInt(DATABASE.COLUMN_SA_ID));
        sale.setSlspId(rs.getInt(DATABASE.COLUMN_SA_SLSP_ID));
        sale.setSiNum(rs.getString(DATABASE.COLUMN_SA_SI_NUM));
        sale.setSaleDate(rs.getString(DATABASE.COLUMN_SA_SALE_DATE));
        sale.setExemptAmount(toAmount(rs.getString(DATABASE.COLUMN_SA_EXEMPT)));
        sale.setZeroRatedAmount(toAmount(rs.getString(DATABASE.COLUMN_SA_ZERO_RATED)));
        sale.setTaxableAmount(toAmount(rs.getString(DATABASE.COLUMN_SA_TAXABLE)));
        return sale;
    }

    private Purchase mapPurchase(ResultSet rs) throws SQLException {
        Purchase purchase = new Purchase();
        purchase.setId(rs.getInt(DATABASE.COLUMN_PU_ID));
        purchase.setSlspId(rs.getInt(DATABASE.COLUMN_PU_SLSP_ID));
        purchase.setSupplierId(rs.getInt(DATABASE.COLUMN_PU_SUPPLIER_ID));
        purchase.setInvoiceNum(rs.getString(DATABASE.COLUMN_PU_INVOICE_NUM));
        purchase.setPurchaseDate(rs.getString(DATABASE.COLUMN_PU_PURCHASE_DATE));
        purchase.setExemptAmount(toAmount(rs.getString(DATABASE.COLUMN_PU_EXEMPT)));
        purchase.setZeroRatedAmount(toAmount(rs.getString(DATABASE.COLUMN_PU_ZERO_RATED)));
        purchase.setTaxableAmount(toAmount(rs.getString(DATABASE.COLUMN_PU_TAXABLE)));
        return purchase;
    }

    private static class DATABASE {

        static final String COLUMN_S_ID = "id";
        static final String COLUMN_S_TAX_ID = "taxId";
        static final String COLUMN_S_PERIOD = "period";
        static final String COLUMN_S_DATE_CREATED = "dateCreated";
        static final String COLUMN_S_DATE_MODIFIED = "dateModified";

        static final String COLUMN_SA_ID = "id";
        static final String COLUMN_SA_SLSP_ID = "slspId";
        static final String COLUMN_SA_SI_NUM = "siNum";
        static final String COLUMN_SA_SALE_DATE = "saleDate";
        static final String COLUMN_SA_EXEMPT = "exemptAmount";
        static final String COLUMN_SA_ZERO_RATED = "zeroRatedAmount";
        static final String COLUMN_SA_TAXABLE = "taxableAmount";

        static final String COLUMN_PU_ID = "id";
        static final String COLUMN_PU_SLSP_ID = "slspId";
        static final String COLUMN_PU_SUPPLIER_ID = "supplierId";
        static final String COLUMN_PU_INVOICE_NUM = "invoiceNum";
        static final String COLUMN_PU_PURCHASE_DATE = "purchaseDate";
        static final String COLUMN_PU_EXEMPT = "exemptAmount";
        static final String COLUMN_PU_ZERO_RATED = "zeroRatedAmount";
        static final String COLUMN_PU_TAXABLE = "taxableAmount";

        static final String SELECT_ALL_SLSP =
                "SELECT * FROM SLSP ORDER BY dateModified DESC";
        static final String SELECT_SLSP_BY_TAX_ID =
                "SELECT * FROM SLSP WHERE taxId = ? ORDER BY period DESC";
        static final String SELECT_SLSP_BY_TAX_ID_BETWEEN =
                "SELECT * FROM SLSP WHERE taxId = ? AND period BETWEEN ? AND ? ORDER BY period";
        static final String SELECT_SLSP_BY_ID =
                "SELECT * FROM SLSP WHERE id = ?";
        static final String EXISTS_SLSP =
                "SELECT 1 FROM SLSP WHERE taxId = ? AND period = ?";
        static final String INSERT_SLSP =
                "INSERT INTO SLSP (taxId, period, dateCreated, dateModified) " +
                "VALUES (?, ?, ?, ?)";
        static final String UPDATE_SLSP =
                "UPDATE SLSP SET period = ?, dateModified = ? WHERE id = ?";
        static final String UPDATE_SLSP_MODIFIED =
                "UPDATE SLSP SET dateModified = ? WHERE id = ?";
        static final String DELETE_SLSP =
                "DELETE FROM SLSP WHERE id = ?";

        static final String SELECT_SALES_BY_SLSP_ID =
                "SELECT * FROM Sale WHERE slspId = ? ORDER BY id";
        static final String INSERT_SALE =
                "INSERT INTO Sale " +
                "(slspId, siNum, saleDate, exemptAmount, zeroRatedAmount, taxableAmount) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        static final String UPDATE_SALE =
                "UPDATE Sale SET " +
                "siNum = ?, " +
                "saleDate = ?, " +
                "exemptAmount = ?, " +
                "zeroRatedAmount = ?, " +
                "taxableAmount = ? " +
                "WHERE id = ?";
        static final String DELETE_SALE =
                "DELETE FROM Sale WHERE id = ?";
        static final String DELETE_SALES_BY_SLSP_ID =
                "DELETE FROM Sale WHERE slspId = ?";

        static final String SELECT_PURCHASES_BY_SLSP_ID =
                "SELECT * FROM Purchase WHERE slspId = ? ORDER BY id";
        static final String INSERT_PURCHASE =
                "INSERT INTO Purchase " +
                "(slspId, supplierId, invoiceNum, purchaseDate, " +
                "exemptAmount, zeroRatedAmount, taxableAmount) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        static final String UPDATE_PURCHASE =
                "UPDATE Purchase SET " +
                "supplierId = ?, " +
                "invoiceNum = ?, " +
                "purchaseDate = ?, " +
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