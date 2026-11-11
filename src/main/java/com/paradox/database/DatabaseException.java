package com.paradox.database;

/**
 * Thrown when the database layer cannot be reached or configured.
 * Unchecked so repository methods stay free of boilerplate throws clauses;
 * the UI layer catches it at the boundary and shows a dialog.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
