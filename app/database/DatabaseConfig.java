package app.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {
    private static final String URL = "jdbc:sqlite:data/jckc-app.db";

    static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
    
}
