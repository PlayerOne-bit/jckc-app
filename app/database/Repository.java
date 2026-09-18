package app.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import app.models.Account;
import app.models.Name;
import app.models.PersonalInfo;
import app.models.Supplier;
import app.models.Taxpayer;

public class Repository {

    private final Connection sql;

    public Repository() throws SQLException {
        sql = DatabaseConfig.getConnection();
    }


    public int addTaxpayer(Taxpayer taxpayer) throws SQLException {
        try {
            sql.setAutoCommit(false);

            int taxId = insertTaxpayerRecord(taxpayer);

            if (taxpayer.getPersonalInfo() != null) {
                taxpayer.getPersonalInfo().setTaxId(taxId);
                insertPersonalInfo(taxpayer.getPersonalInfo());
            }

            if (taxpayer.getAccount() != null) {
                taxpayer.getAccount().setTaxId(taxId);
                insertAccount(taxpayer.getAccount());
            }

            if (taxpayer.getName() != null) {
                taxpayer.getName().setTaxId(taxId);
                insertName(taxpayer.getName());
            }

            sql.commit();
            return taxId;

        } catch (SQLException e) {
            sql.rollback();
            throw e;

        } finally {
            sql.setAutoCommit(true);
        }
    }

    public List<Taxpayer> getAllTaxpayers() throws SQLException {
        List<Taxpayer> taxpayers = new ArrayList<>();

        try (PreparedStatement ps =
                sql.prepareStatement(DATABASE.SELECT_TABLE_TAXPAYER);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                taxpayers.add(mapTaxpayer(rs));
            }
        }

        for (Taxpayer taxpayer : taxpayers) {
            attachRelations(taxpayer);
        }

        return taxpayers;
    }

    public Taxpayer getTaxpayerById(int id) throws SQLException {

        try (PreparedStatement ps =
                sql.prepareStatement(DATABASE.SELECT_TAXPAYER_BY_ID)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    Taxpayer taxpayer = mapTaxpayer(rs);
                    attachRelations(taxpayer);
                    return taxpayer;
                }
            }
        }

        return null;
    }

    public void updateTaxpayer(Taxpayer taxpayer) throws SQLException {

        try {
            sql.setAutoCommit(false);

            try (PreparedStatement ps =
                    sql.prepareStatement(DATABASE.UPDATE_TABLE_TAXPAYER)) {

                ps.setString(1, taxpayer.getTinNum());
                ps.setString(2, taxpayer.getTaxName());
                ps.setString(3, taxpayer.getTradeName());
                ps.setString(4, taxpayer.getBussAddress());
                ps.setString(5, taxpayer.getBussKind());
                ps.setString(6, taxpayer.getPsic());
                ps.setString(7, taxpayer.getBussLine());
                ps.setString(8, taxpayer.getTaxNformTypes());
                ps.setString(9, taxpayer.getVat());
                ps.setInt(10, taxpayer.getId());

                ps.executeUpdate();
            }

            if (taxpayer.getPersonalInfo() != null) {
                updatePersonalInfo(taxpayer.getPersonalInfo());
            }

            if (taxpayer.getAccount() != null) {
                updateAccount(taxpayer.getAccount());
            }

            if (taxpayer.getName() != null) {
                updateName(taxpayer.getName());
            }

            sql.commit();

        } catch (SQLException e) {
            sql.rollback();
            throw e;

        } finally {
            sql.setAutoCommit(true);
        }
    }

    public void deleteTaxpayer(int taxId) throws SQLException {

        try {
            sql.setAutoCommit(false);

            deleteChildByTaxId(
                    DATABASE.DELETE_PERSONAL_INFO_BY_TAX_ID,
                    taxId
            );

            deleteChildByTaxId(
                    DATABASE.DELETE_ACCOUNT_BY_TAX_ID,
                    taxId
            );

            deleteChildByTaxId(
                    DATABASE.DELETE_NAME_BY_TAX_ID,
                    taxId
            );

            deleteChildByTaxId(
                    DATABASE.DELETE_SUPPLIER_BY_TAX_ID,
                    taxId
            );

            try (PreparedStatement ps =
                    sql.prepareStatement(DATABASE.DELETE_TABLE_TAXPAYER)) {

                ps.setInt(1, taxId);
                ps.executeUpdate();
            }

            sql.commit();

        } catch (SQLException e) {
            sql.rollback();
            throw e;

        } finally {
            sql.setAutoCommit(true);
        }
    }

    private int insertTaxpayerRecord(Taxpayer taxpayer)
            throws SQLException {

        try (PreparedStatement ps = sql.prepareStatement(
                DATABASE.INSERT_TABLE_TAXPAYER,
                Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, taxpayer.getTinNum());
            ps.setString(2, taxpayer.getTaxName());
            ps.setString(3, taxpayer.getTradeName());
            ps.setString(4, taxpayer.getBussAddress());
            ps.setString(5, taxpayer.getBussKind());
            ps.setString(6, taxpayer.getPsic());
            ps.setString(7, taxpayer.getBussLine());
            ps.setString(8, taxpayer.getTaxNformTypes());
            ps.setString(9, taxpayer.getVat());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {

                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException(
                "Failed to insert taxpayer, no ID obtained."
        );
    }

    private void insertPersonalInfo(PersonalInfo personalInfo)
            throws SQLException {

        try (PreparedStatement ps =
                sql.prepareStatement(DATABASE.INSERT_TABLE_PERSONAL_INFO)) {

            ps.setInt(1, personalInfo.getTaxId());
            ps.setString(2, personalInfo.getBirthdate());
            ps.setString(3, personalInfo.getBirthplace());
            ps.setString(4, personalInfo.getResidence());
            ps.setString(5, personalInfo.getCivilStatus());
            ps.setString(6, personalInfo.getSpouseName());
            ps.setString(7, personalInfo.getSpouseTin());
            ps.setString(8, personalInfo.getMotherMaidenName());
            ps.setString(9, personalInfo.getFatherName());
            ps.setString(10, personalInfo.getCpNum());

            ps.executeUpdate();
        }
    }

    private void updatePersonalInfo(PersonalInfo personalInfo)
            throws SQLException {

        try (PreparedStatement ps =
                sql.prepareStatement(DATABASE.UPDATE_TABLE_PERSONAL_INFO)) {

            ps.setString(1, personalInfo.getBirthdate());
            ps.setString(2, personalInfo.getBirthplace());
            ps.setString(3, personalInfo.getResidence());
            ps.setString(4, personalInfo.getCivilStatus());
            ps.setString(5, personalInfo.getSpouseName());
            ps.setString(6, personalInfo.getSpouseTin());
            ps.setString(7, personalInfo.getMotherMaidenName());
            ps.setString(8, personalInfo.getFatherName());
            ps.setString(9, personalInfo.getCpNum());
            ps.setInt(10, personalInfo.getId());

            ps.executeUpdate();
        }
    }

    private PersonalInfo getPersonalInfoByTaxId(int taxId)
            throws SQLException {

        try (PreparedStatement ps =
                sql.prepareStatement(
                        DATABASE.SELECT_PERSONAL_INFO_BY_TAX_ID)) {

            ps.setInt(1, taxId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    PersonalInfo personalInfo = new PersonalInfo();

                    personalInfo.setId(
                            rs.getInt(DATABASE.COLUMN_P_ID)
                    );

                    personalInfo.setTaxId(
                            rs.getInt(DATABASE.COLUMN_P_TAX_ID)
                    );

                    personalInfo.setBirthdate(
                            rs.getString(DATABASE.COLUMN_P_BIRTHDATE)
                    );

                    personalInfo.setBirthplace(
                            rs.getString(DATABASE.COLUMN_P_BIRTHPLACE)
                    );

                    personalInfo.setResidence(
                            rs.getString(DATABASE.COLUMN_P_RESIDENCE)
                    );

                    personalInfo.setCivilStatus(
                            rs.getString(DATABASE.COLUMN_P_CIVIL_STATUS)
                    );

                    personalInfo.setSpouseName(
                            rs.getString(DATABASE.COLUMN_P_SPOUSE_NAME)
                    );

                    personalInfo.setSpouseTin(
                            rs.getString(DATABASE.COLUMN_P_SPOUSE_TIN)
                    );

                    personalInfo.setMotherMaidenName(
                            rs.getString(
                                    DATABASE.COLUMN_P_MOTHER_MAIDEN_NAME
                            )
                    );

                    personalInfo.setFatherName(
                            rs.getString(DATABASE.COLUMN_P_FATHER_NAME)
                    );

                    personalInfo.setCpNum(
                            rs.getString(DATABASE.COLUMN_P_CP_NUM)
                    );

                    return personalInfo;
                }
            }
        }

        return null;
    }

    private void insertAccount(Account account)
            throws SQLException {

        try (PreparedStatement ps =
                sql.prepareStatement(DATABASE.INSERT_TABLE_ACCOUNT)) {

            ps.setInt(1, account.getTaxId());
            ps.setString(2, account.getGmailEmail());
            ps.setString(3, account.getGmailPass());
            ps.setString(4, account.getYahooEmail());
            ps.setString(5, account.getYahooPass());
            ps.setString(6, account.getOrusName());
            ps.setString(7, account.getOrusPass());
            ps.setString(8, account.getAfsName());
            ps.setString(9, account.getAfsPass());
            ps.setString(10, account.getFbName());
            ps.setString(11, account.getRecoveryEmail());

            ps.executeUpdate();
        }
    }

    private void updateAccount(Account account)
            throws SQLException {

        try (PreparedStatement ps =
                sql.prepareStatement(DATABASE.UPDATE_TABLE_ACCOUNT)) {

            ps.setString(1, account.getGmailEmail());
            ps.setString(2, account.getGmailPass());
            ps.setString(3, account.getYahooEmail());
            ps.setString(4, account.getYahooPass());
            ps.setString(5, account.getOrusName());
            ps.setString(6, account.getOrusPass());
            ps.setString(7, account.getAfsName());
            ps.setString(8, account.getAfsPass());
            ps.setString(9, account.getFbName());
            ps.setString(10, account.getRecoveryEmail());
            ps.setInt(11, account.getId());

            ps.executeUpdate();
        }
    }

    private Account getAccountByTaxId(int taxId)
            throws SQLException {

        try (PreparedStatement ps =
                sql.prepareStatement(
                        DATABASE.SELECT_ACCOUNT_BY_TAX_ID)) {

            ps.setInt(1, taxId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    Account account = new Account();

                    account.setId(
                            rs.getInt(DATABASE.COLUMN_A_ID)
                    );

                    account.setTaxId(
                            rs.getInt(DATABASE.COLUMN_A_TAX_ID)
                    );

                    account.setGmailEmail(
                            rs.getString(
                                    DATABASE.COLUMN_A_GMAIL_EMAIL
                            )
                    );

                    account.setGmailPass(
                            rs.getString(
                                    DATABASE.COLUMN_A_GMAIL_PASS
                            )
                    );

                    account.setYahooEmail(
                            rs.getString(
                                    DATABASE.COLUMN_A_YAHOO_EMAIL
                            )
                    );

                    account.setYahooPass(
                            rs.getString(
                                    DATABASE.COLUMN_A_YAHOO_PASS
                            )
                    );

                    account.setOrusName(
                            rs.getString(
                                    DATABASE.COLUMN_A_ORUS_NAME
                            )
                    );

                    account.setOrusPass(
                            rs.getString(
                                    DATABASE.COLUMN_A_ORUS_PASS
                            )
                    );

                    account.setAfsName(
                            rs.getString(
                                    DATABASE.COLUMN_A_AFS_NAME
                            )
                    );

                    account.setAfsPass(
                            rs.getString(
                                    DATABASE.COLUMN_A_AFS_PASS
                            )
                    );

                    account.setFbName(
                            rs.getString(
                                    DATABASE.COLUMN_A_FB_NAME
                            )
                    );

                    account.setRecoveryEmail(
                            rs.getString(
                                    DATABASE.COLUMN_A_RECOVERY_EMAIL
                            )
                    );

                    return account;
                }
            }
        }

        return null;
    }

    private void insertName(Name name)
            throws SQLException {

        try (PreparedStatement ps =
                sql.prepareStatement(DATABASE.INSERT_TABLE_NAME)) {

            ps.setInt(1, name.getTaxId());
            ps.setString(2, name.getLastName());
            ps.setString(3, name.getFirstName());
            ps.setString(4, name.getMiddleName());
            ps.setString(5, name.getSuffix());

            ps.executeUpdate();
        }
    }

    private void updateName(Name name)
            throws SQLException {

        try (PreparedStatement ps =
                sql.prepareStatement(DATABASE.UPDATE_TABLE_NAME)) {

            ps.setString(1, name.getLastName());
            ps.setString(2, name.getFirstName());
            ps.setString(3, name.getMiddleName());
            ps.setString(4, name.getSuffix());
            ps.setInt(5, name.getId());

            ps.executeUpdate();
        }
    }

    private Name getNameByTaxId(int taxId)
            throws SQLException {

        try (PreparedStatement ps =
                sql.prepareStatement(
                        DATABASE.SELECT_NAME_BY_TAX_ID)) {

            ps.setInt(1, taxId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    Name name = new Name();

                    name.setId(
                            rs.getInt(DATABASE.COLUMN_N_ID)
                    );

                    name.setTaxId(
                            rs.getInt(DATABASE.COLUMN_N_TAX_ID)
                    );

                    name.setLastName(
                            rs.getString(
                                    DATABASE.COLUMN_N_LAST_NAME
                            )
                    );

                    name.setFirstName(
                            rs.getString(
                                    DATABASE.COLUMN_N_FIRST_NAME
                            )
                    );

                    name.setMiddleName(
                            rs.getString(
                                    DATABASE.COLUMN_N_MIDDLE_NAME
                            )
                    );

                    name.setSuffix(
                            rs.getString(
                                    DATABASE.COLUMN_N_SUFFIX
                            )
                    );

                    return name;
                }
            }
        }

        return null;
    }


    // =========================================================
    // SUPPLIER CRUD (linked to Taxpayer via taxId FK)
    // =========================================================

    public int addSupplier(Supplier supplier)
            throws SQLException {

        try {
            sql.setAutoCommit(false);

            int supplierId = insertSupplierRecord(supplier);

            sql.commit();

            return supplierId;

        } catch (SQLException e) {
            sql.rollback();
            throw e;

        } finally {
            sql.setAutoCommit(true);
        }
    }

    private int insertSupplierRecord(Supplier supplier)
            throws SQLException {

        try (PreparedStatement ps = sql.prepareStatement(
                DATABASE.INSERT_TABLE_SUPPLIER,
                Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, supplier.getTinNum());
            ps.setString(2, supplier.getTradeName());
            ps.setString(3, supplier.getBussAddress());
            ps.setInt(4, supplier.getTaxId());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {

                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException(
                "Failed to insert supplier, no ID obtained."
        );
    }

    public List<Supplier> getAllSuppliers()
            throws SQLException {

        List<Supplier> suppliers = new ArrayList<>();

        try (PreparedStatement ps =
                sql.prepareStatement(DATABASE.SELECT_TABLE_SUPPLIER);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                suppliers.add(mapSupplier(rs));
            }
        }

        return suppliers;
    }

    public Supplier getSupplierById(int id)
            throws SQLException {

        try (PreparedStatement ps =
                sql.prepareStatement(
                        DATABASE.SELECT_SUPPLIER_BY_ID)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return mapSupplier(rs);
                }
            }
        }

        return null;
    }

    public List<Supplier> getSuppliersByTaxId(int taxId)
            throws SQLException {

        List<Supplier> suppliers = new ArrayList<>();

        try (PreparedStatement ps =
                sql.prepareStatement(
                        DATABASE.SELECT_SUPPLIER_BY_TAX_ID)) {

            ps.setInt(1, taxId);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    suppliers.add(mapSupplier(rs));
                }
            }
        }

        return suppliers;
    }

    public void updateSupplier(Supplier supplier)
            throws SQLException {

        try (PreparedStatement ps =
                sql.prepareStatement(
                        DATABASE.UPDATE_TABLE_SUPPLIER)) {

            ps.setString(1, supplier.getTinNum());
            ps.setString(2, supplier.getTradeName());
            ps.setString(3, supplier.getBussAddress());
            ps.setInt(4, supplier.getTaxId());
            ps.setInt(5, supplier.getId());

            ps.executeUpdate();
        }
    }

    public void deleteSupplier(int id)
            throws SQLException {

        try (PreparedStatement ps =
                sql.prepareStatement(
                        DATABASE.DELETE_TABLE_SUPPLIER)) {

            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }


    private Taxpayer mapTaxpayer(ResultSet rs)
            throws SQLException {

        Taxpayer taxpayer = new Taxpayer();

        taxpayer.setId(
                rs.getInt(DATABASE.COLUMN_T_ID)
        );

        taxpayer.setTinNum(
                rs.getString(DATABASE.COLUMN_T_TIN_NUM)
        );

        taxpayer.setTaxName(
                rs.getString(DATABASE.COLUMN_T_TAX_NAME)
        );

        taxpayer.setTradeName(
                rs.getString(DATABASE.COLUMN_T_TRADE_NAME)
        );

        taxpayer.setBussAddress(
                rs.getString(DATABASE.COLUMN_T_BUSS_ADDRESS)
        );

        taxpayer.setBussKind(
                rs.getString(DATABASE.COLUMN_T_BUSS_KIND)
        );

        taxpayer.setPsic(
                rs.getString(DATABASE.COLUMN_T_PSIC)
        );

        taxpayer.setBussLine(
                rs.getString(DATABASE.COLUMN_T_BUSS_LINE)
        );

        taxpayer.setTaxNformTypes(
                rs.getString(DATABASE.COLUMN_T_TAX_NFORM_TYPES)
        );

        taxpayer.setVat(
                rs.getString(DATABASE.COLUMN_T_VAT)
        );

        return taxpayer;
    }

    private Supplier mapSupplier(ResultSet rs)
            throws SQLException {

        Supplier supplier = new Supplier();

        supplier.setId(
                rs.getInt(DATABASE.COLUMN_S_ID)
        );

        supplier.setTinNum(
                rs.getString(DATABASE.COLUMN_S_TIN_NUM)
        );

        supplier.setTradeName(
                rs.getString(DATABASE.COLUMN_S_TRADE_NAME)
        );

        supplier.setBussAddress(
                rs.getString(DATABASE.COLUMN_S_BUSS_ADDRESS)
        );

        supplier.setTaxId(
                rs.getInt(DATABASE.COLUMN_S_TAX_ID)
        );

        return supplier;
    }

    private void attachRelations(Taxpayer taxpayer)
            throws SQLException {

        taxpayer.setPersonalInfo(
                getPersonalInfoByTaxId(taxpayer.getId())
        );

        taxpayer.setAccount(
                getAccountByTaxId(taxpayer.getId())
        );

        taxpayer.setName(
                getNameByTaxId(taxpayer.getId())
        );
    }


    private void deleteChildByTaxId(
            String query,
            int taxId
    ) throws SQLException {

        try (PreparedStatement ps =
                sql.prepareStatement(query)) {

            ps.setInt(1, taxId);
            ps.executeUpdate();
        }
    }

    private static class DATABASE {

        static final String COLUMN_T_ID = "id";
        static final String COLUMN_T_TIN_NUM = "tinNum";
        static final String COLUMN_T_TAX_NAME = "taxName";
        static final String COLUMN_T_TRADE_NAME = "tradeName";
        static final String COLUMN_T_BUSS_ADDRESS = "bussAddress";
        static final String COLUMN_T_BUSS_KIND = "bussKind";
        static final String COLUMN_T_PSIC = "psic";
        static final String COLUMN_T_BUSS_LINE = "bussLine";
        static final String COLUMN_T_TAX_NFORM_TYPES = "taxNformTypes";
        static final String COLUMN_T_VAT = "vat";

        static final String COLUMN_P_ID = "id";
        static final String COLUMN_P_TAX_ID = "taxId";
        static final String COLUMN_P_BIRTHDATE = "birthdate";
        static final String COLUMN_P_BIRTHPLACE = "birthplace";
        static final String COLUMN_P_RESIDENCE = "residence";
        static final String COLUMN_P_CIVIL_STATUS = "civilStatus";
        static final String COLUMN_P_SPOUSE_NAME = "spouseName";
        static final String COLUMN_P_SPOUSE_TIN = "spouseTin";
        static final String COLUMN_P_MOTHER_MAIDEN_NAME =
                "motherMaidenName";
        static final String COLUMN_P_FATHER_NAME = "fatherName";
        static final String COLUMN_P_CP_NUM = "cpNum";

        static final String COLUMN_A_ID = "id";
        static final String COLUMN_A_TAX_ID = "taxId";
        static final String COLUMN_A_GMAIL_EMAIL = "gmailEmail";
        static final String COLUMN_A_GMAIL_PASS = "gmailPass";
        static final String COLUMN_A_YAHOO_EMAIL = "yahooEmail";
        static final String COLUMN_A_YAHOO_PASS = "yahooPass";
        static final String COLUMN_A_ORUS_NAME = "orusName";
        static final String COLUMN_A_ORUS_PASS = "orusPass";
        static final String COLUMN_A_AFS_NAME = "afsName";
        static final String COLUMN_A_AFS_PASS = "afsPass";
        static final String COLUMN_A_FB_NAME = "fbName";
        static final String COLUMN_A_RECOVERY_EMAIL = "recoveryEmail";

        static final String COLUMN_N_ID = "id";
        static final String COLUMN_N_TAX_ID = "taxId";
        static final String COLUMN_N_LAST_NAME = "lastName";
        static final String COLUMN_N_FIRST_NAME = "firstName";
        static final String COLUMN_N_MIDDLE_NAME = "middleName";
        static final String COLUMN_N_SUFFIX = "suffix";

        static final String COLUMN_S_ID = "id";
        static final String COLUMN_S_TIN_NUM = "tinNum";
        static final String COLUMN_S_TRADE_NAME = "tradeName";
        static final String COLUMN_S_BUSS_ADDRESS = "bussAddress";
        static final String COLUMN_S_TAX_ID = "taxId";

        static final String SELECT_TABLE_TAXPAYER =
                "SELECT * FROM Taxpayer";

        static final String SELECT_PERSONAL_INFO_BY_TAX_ID =
                "SELECT * FROM PersonalInfo WHERE taxId = ?";

        static final String SELECT_ACCOUNT_BY_TAX_ID =
                "SELECT * FROM Account WHERE taxId = ?";

        static final String SELECT_NAME_BY_TAX_ID =
                "SELECT * FROM Name WHERE taxId = ?";

        static final String SELECT_TABLE_SUPPLIER =
                "SELECT * FROM Supplier";

        static final String SELECT_TAXPAYER_BY_ID =
                "SELECT * FROM Taxpayer WHERE id = ?";

        static final String SELECT_SUPPLIER_BY_ID =
                "SELECT * FROM Supplier WHERE id = ?";

        static final String SELECT_SUPPLIER_BY_TAX_ID =
                "SELECT * FROM Supplier WHERE taxId = ?";

        static final String INSERT_TABLE_TAXPAYER =
                "INSERT INTO Taxpayer " +
                "(tinNum, taxName, tradeName, bussAddress, bussKind, " +
                "psic, bussLine, taxNformTypes, vat) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        static final String INSERT_TABLE_PERSONAL_INFO =
                "INSERT INTO PersonalInfo " +
                "(taxId, birthdate, birthplace, residence, civilStatus, " +
                "spouseName, spouseTin, motherMaidenName, fatherName, cpNum) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        static final String INSERT_TABLE_ACCOUNT =
                "INSERT INTO Account " +
                "(taxId, gmailEmail, gmailPass, yahooEmail, yahooPass, " +
                "orusName, orusPass, afsName, afsPass, fbName, " +
                "recoveryEmail) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        static final String INSERT_TABLE_NAME =
                "INSERT INTO Name " +
                "(taxId, lastName, firstName, middleName, suffix) " +
                "VALUES (?, ?, ?, ?, ?)";

        static final String INSERT_TABLE_SUPPLIER =
                "INSERT INTO Supplier " +
                "(tinNum, tradeName, bussAddress, taxId) " +
                "VALUES (?, ?, ?, ?)";

        static final String UPDATE_TABLE_TAXPAYER =
                "UPDATE Taxpayer SET " +
                "tinNum = ?, " +
                "taxName = ?, " +
                "tradeName = ?, " +
                "bussAddress = ?, " +
                "bussKind = ?, " +
                "psic = ?, " +
                "bussLine = ?, " +
                "taxNformTypes = ?, " +
                "vat = ? " +
                "WHERE id = ?";

        static final String UPDATE_TABLE_PERSONAL_INFO =
                "UPDATE PersonalInfo SET " +
                "birthdate = ?, " +
                "birthplace = ?, " +
                "residence = ?, " +
                "civilStatus = ?, " +
                "spouseName = ?, " +
                "spouseTin = ?, " +
                "motherMaidenName = ?, " +
                "fatherName = ?, " +
                "cpNum = ? " +
                "WHERE id = ?";

        static final String UPDATE_TABLE_ACCOUNT =
                "UPDATE Account SET " +
                "gmailEmail = ?, " +
                "gmailPass = ?, " +
                "yahooEmail = ?, " +
                "yahooPass = ?, " +
                "orusName = ?, " +
                "orusPass = ?, " +
                "afsName = ?, " +
                "afsPass = ?, " +
                "fbName = ?, " +
                "recoveryEmail = ? " +
                "WHERE id = ?";

        static final String UPDATE_TABLE_NAME =
                "UPDATE Name SET " +
                "lastName = ?, " +
                "firstName = ?, " +
                "middleName = ?, " +
                "suffix = ? " +
                "WHERE id = ?";

        static final String UPDATE_TABLE_SUPPLIER =
                "UPDATE Supplier SET " +
                "tinNum = ?, " +
                "tradeName = ?, " +
                "bussAddress = ?, " +
                "taxId = ? " +
                "WHERE id = ?";

        static final String DELETE_TABLE_TAXPAYER =
                "DELETE FROM Taxpayer WHERE id = ?";

        static final String DELETE_PERSONAL_INFO_BY_TAX_ID =
                "DELETE FROM PersonalInfo WHERE taxId = ?";

        static final String DELETE_ACCOUNT_BY_TAX_ID =
                "DELETE FROM Account WHERE taxId = ?";

        static final String DELETE_NAME_BY_TAX_ID =
                "DELETE FROM Name WHERE taxId = ?";

        static final String DELETE_TABLE_SUPPLIER =
                "DELETE FROM Supplier WHERE id = ?";

        static final String DELETE_SUPPLIER_BY_TAX_ID =
                "DELETE FROM Supplier WHERE taxId = ?";
    }
}