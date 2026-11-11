package com.paradox.util;

/**
 * Thrown when a user operation violates a simulation rule (invalid instance
 * counts, duplicate names, releasing resources not held, ...). Controllers
 * catch it and show the message in a dialog.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
