package app.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {

    private static final String URL =
            "jdbc:sqlite:data/jckc-app.db";

    private DatabaseConfig() {
    }

    static Connection getConnection() throws SQLException {

        Connection connection = DriverManager.getConnection(URL);

        return connection;
    }

}