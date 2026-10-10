package app.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import app.models.BirMail;

/**
 * Local cache of BIR confirmation emails, so the page can show them without
 * waiting on Gmail/Yahoo. Rows are keyed by taxpayer + mail account + server uid.
 */
public class BirMailRepository {

    private final Connection sql;

    public BirMailRepository() throws SQLException {
        sql = DatabaseConfig.getConnection();
        try (Statement st = sql.createStatement()) {
            st.execute(DATABASE.CREATE_TABLE);   // no-op when it already exists
        }
    }

    /** Saved messages for this taxpayer + mail account, newest first. */
    public List<BirMail> getAll(int taxId, String account) throws SQLException {
        List<BirMail> list = new ArrayList<>();
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_ALL)) {
            ps.setInt(1, taxId);
            ps.setString(2, account);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapMail(rs));
            }
        }
        return list;
    }

    /** Highest server uid saved so far (0 when nothing is cached). */
    public long getMaxUid(int taxId, String account) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_MAX_UID)) {
            ps.setInt(1, taxId);
            ps.setString(2, account);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** The uidValidity the cached rows were saved under, or null when nothing is cached. */
    public Long getUidValidity(int taxId, String account) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.SELECT_UID_VALIDITY)) {
            ps.setInt(1, taxId);
            ps.setString(2, account);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** Saves new messages in one transaction. Messages already saved are skipped. */
    public void addAll(int taxId, String account, long uidValidity, List<BirMail> mails) throws SQLException {
        if (mails.isEmpty()) return;
        try {
            sql.setAutoCommit(false);
            try (PreparedStatement ps = sql.prepareStatement(DATABASE.INSERT_MAIL)) {
                for (BirMail m : mails) {
                    ps.setInt(1, taxId);
                    ps.setString(2, account);
                    ps.setLong(3, m.uid());
                    ps.setLong(4, uidValidity);
                    ps.setString(5, m.received().toString());
                    ps.setString(6, m.subject());
                    ps.setString(7, m.from());
                    ps.setString(8, m.replyTo());
                    ps.setString(9, m.to());
                    ps.setString(10, String.join("\n", m.attachments()));
                    ps.setString(11, m.html());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            sql.commit();
        } catch (SQLException e) {
            sql.rollback();
            throw e;
        } finally {
            sql.setAutoCommit(true);
        }
    }

    /** Clears the cache for one mail account (used when the server's uidValidity changes). */
    public void deleteAll(int taxId, String account) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement(DATABASE.DELETE_ALL)) {
            ps.setInt(1, taxId);
            ps.setString(2, account);
            ps.executeUpdate();
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private BirMail mapMail(ResultSet rs) throws SQLException {
        String att = nz(rs.getString(DATABASE.COLUMN_ATTACHMENTS));
        List<String> names = att.isBlank() ? List.of() : List.of(att.split("\n"));
        return new BirMail(
                rs.getLong(DATABASE.COLUMN_UID),
                LocalDateTime.parse(rs.getString(DATABASE.COLUMN_RECEIVED)),
                nz(rs.getString(DATABASE.COLUMN_SUBJECT)),
                nz(rs.getString(DATABASE.COLUMN_FROM)),
                nz(rs.getString(DATABASE.COLUMN_REPLY_TO)),
                nz(rs.getString(DATABASE.COLUMN_TO)),
                names,
                rs.getString(DATABASE.COLUMN_HTML));
    }

    private static class DATABASE {

        // "from" and "to" are reserved words in SQL, hence fromAddr / toAddr
        static final String COLUMN_UID = "uid";
        static final String COLUMN_RECEIVED = "received";
        static final String COLUMN_SUBJECT = "subject";
        static final String COLUMN_FROM = "fromAddr";
        static final String COLUMN_REPLY_TO = "replyTo";
        static final String COLUMN_TO = "toAddr";
        static final String COLUMN_ATTACHMENTS = "attachments";
        static final String COLUMN_HTML = "html";

        static final String CREATE_TABLE =
                "CREATE TABLE IF NOT EXISTS BirMail (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "taxId INTEGER NOT NULL, " +
                "account TEXT NOT NULL, " +
                "uid INTEGER NOT NULL, " +
                "uidValidity INTEGER NOT NULL, " +
                "received TEXT NOT NULL, " +
                "subject TEXT, " +
                "fromAddr TEXT, " +
                "replyTo TEXT, " +
                "toAddr TEXT, " +
                "attachments TEXT, " +
                "html TEXT, " +
                "UNIQUE (taxId, account, uid))";

        static final String SELECT_ALL =
                "SELECT * FROM BirMail WHERE taxId = ? AND account = ? ORDER BY received DESC";
        static final String SELECT_MAX_UID =
                "SELECT COALESCE(MAX(uid), 0) FROM BirMail WHERE taxId = ? AND account = ?";
        static final String SELECT_UID_VALIDITY =
                "SELECT uidValidity FROM BirMail WHERE taxId = ? AND account = ? LIMIT 1";
        static final String INSERT_MAIL =
                "INSERT OR IGNORE INTO BirMail " +
                "(taxId, account, uid, uidValidity, received, subject, fromAddr, replyTo, toAddr, attachments, html) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        static final String DELETE_ALL =
                "DELETE FROM BirMail WHERE taxId = ? AND account = ?";
    }
}