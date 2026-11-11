package com.paradox.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs a unit of work on a single connection with commit/rollback.
 *
 * Services group every state-changing operation (allocation, release,
 * recovery, snapshots) through this helper so the database never observes a
 * half-applied change - matching the "use transactions" requirement.
 */
public final class Db {

    private Db() {
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    /**
     * Executes work atomically: commits on success, rolls back on any
     * exception, and always closes the connection.
     */
    public static <T> T inTransaction(SqlFunction<T> work) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database transaction failed", e);
        }
    }
}
