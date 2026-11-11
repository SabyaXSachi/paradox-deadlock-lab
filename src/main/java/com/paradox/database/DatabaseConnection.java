package com.paradox.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Provides JDBC connections to paradox_db.
 *
 * Every call to {@link #getConnection()} returns a fresh connection that the
 * caller must close (use try-with-resources). Keeping this class tiny and
 * static makes it usable from repositories, the schema initializer and tests
 * without a dependency-injection framework.
 */
public final class DatabaseConnection {

    private static volatile DatabaseConfig config;

    private DatabaseConnection() {
    }

    /** Replaces the configuration; used by tests and optional runtime overrides. */
    public static void setConfig(DatabaseConfig newConfig) {
        config = newConfig;
    }

    private static DatabaseConfig config() {
        if (config == null) {
            config = new DatabaseConfig();
        }
        return config;
    }

    public static Connection getConnection() throws SQLException {
        DatabaseConfig cfg = config();
        // The MySQL Connector/J driver is on the classpath and self-registers.
        return DriverManager.getConnection(cfg.getUrl(), cfg.getUser(), cfg.getPassword());
    }

    /** Quick reachability probe used at application startup. */
    public static boolean isReachable() {
        try (Connection connection = getConnection()) {
            return connection.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }
}
