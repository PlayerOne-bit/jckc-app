package app.database;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {

    private static final String DB_NAME = "jckc-app.db";
    private static final String URL = "jdbc:sqlite:" + resolveDbPath().toAbsolutePath();

    private DatabaseConfig() {
    }

    static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    private static Path resolveDbPath() {
        try {
            // Fixed, writable location that survives uninstalls and upgrades
            Path dir = Paths.get(System.getProperty("user.home"), ".jckc");
            Files.createDirectories(dir);
            Path db = dir.resolve(DB_NAME);

            // First run: copy the starter DB shipped with the app, if there is one
            if (Files.notExists(db)) {
                Path bundled = findBundledDb();
                if (bundled != null) {
                    Files.copy(bundled, db);
                }
            }
            return db;
        } catch (Exception e) {
            throw new RuntimeException("Cannot prepare database", e);
        }
    }

    private static Path findBundledDb() {
        try {
            // Folder that contains the app jar (the "app" folder in the jpackage output)
            Path jarDir = Paths.get(DatabaseConfig.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            if (jarDir != null) {
                Path p = jarDir.resolve("data").resolve(DB_NAME);
                if (Files.exists(p)) return p;
            }
        } catch (URISyntaxException | RuntimeException ignored) {
        }
        // Fallback for running from Eclipse (project folder)
        Path dev = Paths.get("data", DB_NAME);
        return Files.exists(dev) ? dev : null;
    }
}