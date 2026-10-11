package app.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConfig {

    // =========================================================
    // DATABASE CONFIGURATION
    // =========================================================

    private static final Path DATABASE_DIRECTORY =
            Paths.get("data").toAbsolutePath().normalize();

    private static final Path DATABASE_PATH =
            DATABASE_DIRECTORY.resolve("jckc-app.db");

    private static final String URL =
            "jdbc:sqlite:" + DATABASE_PATH;

    private DatabaseConfig() {
        // Prevent instantiation.
    }

    // =========================================================
    // CREATE DATABASE DIRECTORY AND OPEN CONNECTION
    // =========================================================

    private static void initializeDatabaseFile() throws SQLException {

        try {
            Files.createDirectories(DATABASE_DIRECTORY);

        } catch (IOException e) {
            throw new SQLException(
                    "Unable to create database directory: "
                            + DATABASE_DIRECTORY,
                    e
            );
        }
    }

    static Connection getConnection() throws SQLException {

        initializeDatabaseFile();

        Connection connection = DriverManager.getConnection(URL);

        try (Statement statement = connection.createStatement()) {

            // Enable foreign key constraints for this connection.
            statement.execute("PRAGMA foreign_keys = ON;");

            // Wait up to 10 seconds if another connection locks the DB.
            statement.execute("PRAGMA busy_timeout = 10000;");

        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                e.addSuppressed(closeException);
            }

            throw e;
        }

        return connection;
    }

    // =========================================================
    // INITIALIZE ALL DATABASE TABLES
    // =========================================================

    public static void initializeTables() throws SQLException {

        try (Connection connection = getConnection()) {

            // Create all tables in one transaction.
            connection.setAutoCommit(false);

            try (Statement statement = connection.createStatement()) {

                // -------------------------------------------------
                // 1. TAXPAYER
                // Parent table for PersonalInfo, Account, Name,
                // Supplier, SLSP, and other taxpayer-related data.
                // -------------------------------------------------

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS Taxpayer (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        tinNum INTEGER NOT NULL UNIQUE,
                        tradeName TEXT NOT NULL,
                        bussAddress TEXT NOT NULL,
                        bussKind TEXT,
                        psic TEXT,
                        formTypes TEXT,
                        vat TEXT,
                        appPassword TEXT
                    );
                """);

                // -------------------------------------------------
                // 2. PERSONAL INFO
                // -------------------------------------------------

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS PersonalInfo (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        taxId INTEGER NOT NULL,
                        birthdate DATE,
                        birthplace TEXT,
                        residence TEXT,
                        civilStatus TEXT,
                        spouseName TEXT,
                        spouseTin TEXT,
                        motherMaidenName TEXT,
                        fatherName TEXT,
                        cpNum TEXT,

                        FOREIGN KEY (taxId)
                            REFERENCES Taxpayer(id)
                            ON DELETE CASCADE
                    );
                """);

                // -------------------------------------------------
                // 3. ACCOUNT
                // -------------------------------------------------

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS Account (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        taxId INTEGER NOT NULL,
                        gmailEmail TEXT,
                        gmailPass TEXT,
                        yahooEmail TEXT,
                        yahooPass INTEGER,
                        orusName TEXT,
                        orusPass TEXT,
                        afsName TEXT,
                        afsPass TEXT,
                        fbName TEXT,
                        recoveryEmail TEXT,

                        FOREIGN KEY (taxId)
                            REFERENCES Taxpayer(id)
                            ON DELETE CASCADE
                    );
                """);

                // -------------------------------------------------
                // 4. NAME
                // -------------------------------------------------

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS Name (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        taxId INTEGER NOT NULL,
                        lastName TEXT,
                        firstName TEXT,
                        middleName TEXT,
                        suffix TEXT,

                        FOREIGN KEY (taxId)
                            REFERENCES Taxpayer(id)
                            ON DELETE CASCADE
                    );
                """);

                // -------------------------------------------------
                // 5. SUPPLIER
                // -------------------------------------------------

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS Supplier (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        taxId INTEGER NOT NULL,
                        tinNum TEXT NOT NULL,
                        tradeName TEXT NOT NULL,
                        bussAddress TEXT NOT NULL,

                        FOREIGN KEY (taxId)
                            REFERENCES Taxpayer(id)
                            ON DELETE CASCADE
                    );
                """);

                // -------------------------------------------------
                // 6. SLSP
                // -------------------------------------------------

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS SLSP (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        taxId INTEGER NOT NULL,
                        period TEXT NOT NULL,
                        dateCreated TEXT NOT NULL,
                        dateModified TEXT NOT NULL,

                        FOREIGN KEY (taxId)
                            REFERENCES Taxpayer(id)
                            ON DELETE CASCADE
                    );
                """);

                // -------------------------------------------------
                // 7. SALE
                // -------------------------------------------------

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS Sale (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        slspId INTEGER NOT NULL,
                        saleDate TEXT,
                        siNum TEXT,
                        exemptAmount TEXT NOT NULL,
                        zeroRatedAmount TEXT NOT NULL,
                        grossAmount TEXT,

                        FOREIGN KEY (slspId)
                            REFERENCES SLSP(id)
                            ON DELETE CASCADE
                    );
                """);

                // -------------------------------------------------
                // 8. PURCHASE
                // -------------------------------------------------

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS Purchase (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        slspId INTEGER NOT NULL,
                        supplierId INTEGER NOT NULL,
                        purchaseDate TEXT,
                        invoiceNum TEXT,
                        exemptAmount TEXT NOT NULL,
                        zeroRatedAmount TEXT NOT NULL,
                        grossAmount TEXT,

                        FOREIGN KEY (slspId)
                            REFERENCES SLSP(id)
                            ON DELETE CASCADE,

                        FOREIGN KEY (supplierId)
                            REFERENCES Supplier(id)
                            ON DELETE CASCADE
                    );
                """);

                // -------------------------------------------------
                // 9. BIR MAIL
                // -------------------------------------------------

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS BirMail (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        taxId INTEGER NOT NULL,
                        account TEXT NOT NULL,
                        uid INTEGER NOT NULL,
                        uidValidity INTEGER NOT NULL,
                        received TEXT NOT NULL,
                        subject TEXT,
                        fromAddr TEXT,
                        replyTo TEXT,
                        toAddr TEXT,
                        attachments TEXT,
                        html TEXT,

                        UNIQUE (taxId, account, uid),

                        FOREIGN KEY (taxId)
                            REFERENCES Taxpayer(id)
                            ON DELETE CASCADE
                    );
                """);

                // -------------------------------------------------
                // 10. VERIFY DATABASE CONNECTION SETTINGS
                // -------------------------------------------------

                statement.execute("PRAGMA foreign_keys = ON;");
            }

            connection.commit();

        } catch (SQLException e) {
            throw new SQLException(
                    "Failed to initialize the JCKC database tables.",
                    e
            );
        }
    }

    // =========================================================
    // DATABASE PATH
    // Useful for debugging and checking the database location.
    // =========================================================

    public static String getDatabasePath() {
        return DATABASE_PATH.toString();
    }
}