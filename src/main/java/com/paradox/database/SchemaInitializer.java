package com.paradox.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the Paradox tables on first launch by executing database/schema.sql.
 *
 * A fresh MySQL install therefore only needs the CREATE DATABASE step; the
 * schema file itself remains the single source of truth and can still be run
 * manually with any MySQL client.
 */
public final class SchemaInitializer {

    private static final Path SCHEMA_FILE = Path.of("database", "schema.sql");
    private static final String MARKER_TABLE = "simulations";

    private SchemaInitializer() {
    }

    /** True when all Paradox tables already exist in the current database. */
    public static boolean isSchemaPresent() throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM information_schema.TABLES
                WHERE table_schema = DATABASE()
                  AND table_name IN ('simulations', 'processes', 'resources',
                       'resource_allocation', 'resource_requests', 'process_resource_max',
                       'deadlock_events', 'recovery_logs', 'simulation_snapshots',
                       'simulation_events')
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1) == 10;
        }
    }

    /**
     * Creates the tables if they are missing. Safe to call on every startup.
     *
     * @return true when the schema script was executed now
     */
    public static boolean initializeIfMissing() throws SQLException {
        if (isSchemaPresent()) {
            return false;
        }
        executeSchema();
        return true;
    }

    /** Executes every statement in database/schema.sql against the current database. */
    public static void executeSchema() throws SQLException {
        String script = readSchemaFile();
        List<String> statements = splitStatements(script);
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private static String readSchemaFile() {
        if (!Files.exists(SCHEMA_FILE)) {
            throw new DatabaseException("Schema file not found: "
                    + SCHEMA_FILE.toAbsolutePath()
                    + ". Start the application from the project root directory.");
        }
        try {
            return Files.readString(SCHEMA_FILE);
        } catch (IOException e) {
            throw new DatabaseException("Could not read " + SCHEMA_FILE, e);
        }
    }

    /**
     * Splits the script on semicolons that end a statement. The Paradox schema
     * contains no semicolons inside string literals, so a line-based scan is
     * sufficient and keeps the code easy to follow.
     */
    private static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue; // skip comments and blank lines
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = current.toString().strip();
                if (sql.length() > 1) {
                    statements.add(sql.substring(0, sql.length() - 1));
                }
                current.setLength(0);
            }
        }
        String rest = current.toString().strip();
        if (!rest.isEmpty()) {
            statements.add(rest);
        }
        return statements;
    }
}
