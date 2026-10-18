package app.database;

import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.imports.ImportValidator;
import app.models.ColumnMapping;
import app.models.ImportResult;
import app.models.ImportRow;
import app.models.ImportType;

/**
 * All database work for Excel imports. One import = one transaction (all rows or none).
 * Pass the same Connection your other repositories use.
 */
public class ImportRepository {
    private final Connection conn;

    public ImportRepository(Connection conn) { this.conn = conn; }

    // ---------- schema ----------

    /** Safe to call every time the import screen opens. */
    public void ensureSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS ImportProfile ("
                    + "taxId INTEGER NOT NULL, docType TEXT NOT NULL, mapping TEXT NOT NULL, "
                    + "PRIMARY KEY (taxId, docType))");
            st.execute("CREATE TABLE IF NOT EXISTS ImportBatch ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, taxId INTEGER NOT NULL, docType TEXT NOT NULL, "
                    + "fileName TEXT, importedAt TEXT NOT NULL, rowCount INTEGER NOT NULL DEFAULT 0)");
        }
        addColumnIfMissing("Purchase", "batchId", "INTEGER");
        addColumnIfMissing("Sale", "batchId", "INTEGER");
    }

    private void addColumnIfMissing(String table, String column, String def) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return;
            }
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + def);
        }
    }

    // ---------- saved column mappings ----------

    public ColumnMapping loadProfile(int taxId, ImportType type) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT mapping FROM ImportProfile WHERE taxId = ? AND docType = ?")) {
            ps.setInt(1, taxId);
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? ColumnMapping.parse(rs.getString(1)) : null;
            }
        }
    }

    public void saveProfile(int taxId, ImportType type, ColumnMapping mapping) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO ImportProfile (taxId, docType, mapping) VALUES (?, ?, ?)")) {
            ps.setInt(1, taxId);
            ps.setString(2, type.name());
            ps.setString(3, mapping.serialize());
            ps.executeUpdate();
        }
    }

    // ---------- duplicate keys ----------

    /** Keys (see ImportValidator.key) of everything this taxpayer already has. */
    public Set<String> loadExistingKeys(int taxId, ImportType type) throws SQLException {
        Set<String> keys = new HashSet<>();
        if (type == ImportType.PURCHASE) {
            String sql = "SELECT sp.tinNum, sp.tradeName, p.invoiceNum FROM Purchase p "
                    + "JOIN SLSP l ON l.id = p.slspId JOIN Supplier sp ON sp.id = p.supplierId "
                    + "WHERE l.taxId = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, taxId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        keys.add(ImportValidator.purchaseKey(rs.getString(1), rs.getString(2), rs.getString(3)));
                    }
                }
            }
        } else {
            String sql = "SELECT x.siNum FROM Sale x JOIN SLSP l ON l.id = x.slspId WHERE l.taxId = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, taxId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) keys.add(ImportValidator.saleKey(rs.getString(1)));
                }
            }
        }
        return keys;
    }

    // ---------- import / undo ----------

    /**
     * Inserts every importable row in ONE transaction.
     * @param replaceMonths true = first delete this taxpayer's existing rows of the same type in the
     *                      months being imported (validate with an empty existing-key set in that case)
     */
    public ImportResult importRows(int taxId, ImportType type, List<ImportRow> rows,
                                   boolean replaceMonths, String fileName) throws SQLException {
        List<ImportRow> todo = new ArrayList<>();
        for (ImportRow r : rows) if (r.isImportable()) todo.add(r);

        ImportResult res = new ImportResult();
        res.setSkipped(rows.size() - todo.size());
        if (todo.isEmpty()) return res;

        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int batchId = insertBatch(taxId, type, fileName);

            Map<YearMonth, Integer> slsp = new HashMap<>();
            for (ImportRow r : todo) {
                if (!slsp.containsKey(r.getYearMonth())) {
                    slsp.put(r.getYearMonth(), getOrCreateSlsp(taxId, r.getYearMonth(), res));
                }
            }
            if (replaceMonths) deleteExisting(type, slsp.values());

            int inserted = (type == ImportType.PURCHASE)
                    ? insertPurchases(taxId, todo, slsp, batchId, res)
                    : insertSales(todo, slsp, batchId);
            res.setInserted(inserted);

            touchSlsp(slsp.values());
            try (PreparedStatement ps = conn.prepareStatement("UPDATE ImportBatch SET rowCount = ? WHERE id = ?")) {
                ps.setInt(1, inserted);
                ps.setInt(2, batchId);
                ps.executeUpdate();
            }
            conn.commit();
            res.setBatchId(batchId);
            return res;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    /** Removes the purchases/sales of one import. Created suppliers and SLSP months are kept. */
    public int undoBatch(int batchId) throws SQLException {
        boolean previous = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int removed = 0;
            for (String sql : new String[]{
                    "DELETE FROM Purchase WHERE batchId = ?", "DELETE FROM Sale WHERE batchId = ?"}) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, batchId);
                    removed += ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ImportBatch WHERE id = ?")) {
                ps.setInt(1, batchId);
                ps.executeUpdate();
            }
            conn.commit();
            return removed;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previous);
        }
    }

    /** Latest batch id for the "Undo last import" button, or 0 if none. */
    public int lastBatchId(int taxId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT MAX(id) FROM ImportBatch WHERE taxId = ?")) {
            ps.setInt(1, taxId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ---------- internals ----------

    private int insertBatch(int taxId, ImportType type, String fileName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ImportBatch (taxId, docType, fileName, importedAt, rowCount) "
                        + "VALUES (?, ?, ?, datetime('now'), 0)")) {
            ps.setInt(1, taxId);
            ps.setString(2, type.name());
            ps.setString(3, fileName);
            ps.executeUpdate();
        }
        return lastInsertId();
    }

    private int lastInsertId() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int getOrCreateSlsp(int taxId, YearMonth ym, ImportResult res) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM SLSP WHERE taxId = ? AND period = ? ORDER BY id LIMIT 1")) {
            ps.setInt(1, taxId);
            ps.setString(2, ym.toString());                     // yyyy-MM
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO SLSP (taxId, period, dateCreated, dateModified) "
                        + "VALUES (?, ?, datetime('now'), datetime('now'))")) {
            ps.setInt(1, taxId);
            ps.setString(2, ym.toString());
            ps.executeUpdate();
        }
        res.setSlspCreated(res.getSlspCreated() + 1);
        return lastInsertId();
    }

    private void deleteExisting(ImportType type, Collection<Integer> slspIds) throws SQLException {
        if (slspIds.isEmpty()) return;
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < slspIds.size(); i++) in.append(i == 0 ? "?" : ",?");
        String table = type == ImportType.PURCHASE ? "Purchase" : "Sale";
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM " + table + " WHERE slspId IN (" + in + ")")) {
            int i = 1;
            for (int id : slspIds) ps.setInt(i++, id);
            ps.executeUpdate();
        }
    }

    private int insertPurchases(int taxId, List<ImportRow> rows, Map<YearMonth, Integer> slsp,
                                int batchId, ImportResult res) throws SQLException {
        Map<String, Integer> supplierCache = new HashMap<>();
        int n = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO Purchase (slspId, supplierId, purchaseDate, invoiceNum, "
                        + "exemptAmount, zeroRatedAmount, grossAmount, batchId) VALUES (?,?,?,?,?,?,?,?)")) {
            for (ImportRow r : rows) {
                int supplierId = getOrCreateSupplier(taxId, r, supplierCache, res);
                ps.setInt(1, slsp.get(r.getYearMonth()));
                ps.setInt(2, supplierId);
                ps.setString(3, r.getDateText());
                ps.setString(4, r.getDocNo());
                ps.setString(5, money(r.getExempt()));
                ps.setString(6, money(r.getZeroRated()));
                ps.setString(7, money(r.getGross()));
                ps.setInt(8, batchId);
                ps.addBatch();
                n++;
            }
            ps.executeBatch();
        }
        return n;
    }

    private int insertSales(List<ImportRow> rows, Map<YearMonth, Integer> slsp, int batchId) throws SQLException {
        int n = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO Sale (slspId, saleDate, siNum, exemptAmount, zeroRatedAmount, grossAmount, batchId) "
                        + "VALUES (?,?,?,?,?,?,?)")) {
            for (ImportRow r : rows) {
                ps.setInt(1, slsp.get(r.getYearMonth()));
                ps.setString(2, r.getDateText());
                ps.setString(3, r.getDocNo());
                ps.setString(4, money(r.getExempt()));
                ps.setString(5, money(r.getZeroRated()));
                ps.setString(6, money(r.getGross()));
                ps.setInt(7, batchId);
                ps.addBatch();
                n++;
            }
            ps.executeBatch();
        }
        return n;
    }

    /** Matches the supplier by TIN (or by name when there is no TIN); creates it if missing. */
    private int getOrCreateSupplier(int taxId, ImportRow r, Map<String, Integer> cache,
                                    ImportResult res) throws SQLException {
        boolean hasTin = !r.getTin().isEmpty();
        String name = !r.getSupplierName().isEmpty() ? r.getSupplierName() : r.getTin();
        String cacheKey = hasTin ? "T:" + r.getTin() : "N:" + name.toUpperCase();
        Integer cached = cache.get(cacheKey);
        if (cached != null) return cached;

        String find = hasTin
                ? "SELECT id FROM Supplier WHERE taxId = ? AND tinNum = ? ORDER BY id LIMIT 1"
                : "SELECT id FROM Supplier WHERE taxId = ? AND tinNum = '' AND UPPER(tradeName) = ? ORDER BY id LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(find)) {
            ps.setInt(1, taxId);
            ps.setString(2, hasTin ? r.getTin() : name.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    cache.put(cacheKey, rs.getInt(1));
                    return rs.getInt(1);
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO Supplier (taxId, tinNum, tradeName, bussAddress) VALUES (?,?,?,?)")) {
            ps.setInt(1, taxId);
            ps.setString(2, hasTin ? r.getTin() : "");
            ps.setString(3, name);
            ps.setString(4, r.getSupplierAddress());
            ps.executeUpdate();
        }
        int id = lastInsertId();
        cache.put(cacheKey, id);
        res.setSuppliersCreated(res.getSuppliersCreated() + 1);
        return id;
    }

    private void touchSlsp(Collection<Integer> ids) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE SLSP SET dateModified = datetime('now') WHERE id = ?")) {
            for (int id : ids) {
                ps.setInt(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static String money(java.math.BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}