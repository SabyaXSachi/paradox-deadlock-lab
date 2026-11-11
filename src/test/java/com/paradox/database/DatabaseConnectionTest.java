package com.paradox.database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test against the real MySQL server.
 *
 * Verifies that the connection settings work, that database/schema.sql is
 * valid SQL, and that all ten Paradox tables exist after initialization.
 * (The algorithm and service unit tests never touch the database; this test
 * intentionally does.)
 */
class DatabaseConnectionTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "simulations", "processes", "resources", "resource_allocation",
            "resource_requests", "process_resource_max", "deadlock_events",
            "recovery_logs", "simulation_snapshots", "simulation_events");

    @Test
    void connectsAndInitializesFullSchema() throws SQLException {
        assertTrue(DatabaseConnection.isReachable(),
                "MySQL must be running and reachable (see README database setup)");

        boolean createdNow = SchemaInitializer.initializeIfMissing();

        try (Connection connection = DatabaseConnection.getConnection()) {
            // Simulations table must exist and accept a full insert/select/delete cycle.
            Statement statement = connection.createStatement();
            assertEquals(1, statement.executeUpdate(
                    "INSERT INTO simulations (name, description) VALUES ('schema-test', 'temporary')"),
                    "insert into simulations should succeed");
            long id;
            try (ResultSet rs = statement.executeQuery(
                    "SELECT simulation_id FROM simulations WHERE name = 'schema-test'")) {
                rs.next();
                id = rs.getLong(1);
            }
            assertEquals(1, statement.executeUpdate(
                    "DELETE FROM simulations WHERE simulation_id = " + id),
                    "cleanup delete should succeed");
            statement.close();

            // All ten tables must be present.
            List<String> found = new ArrayList<>();
            String sql = "SELECT table_name FROM information_schema.TABLES "
                    + "WHERE table_schema = DATABASE()";
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    found.add(rs.getString(1).toLowerCase());
                }
            }
            for (String expected : EXPECTED_TABLES) {
                assertTrue(found.contains(expected), "table missing: " + expected);
            }
        }
    }
}
