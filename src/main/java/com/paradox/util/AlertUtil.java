package com.paradox.util;

import javafx.scene.control.Alert;

/**
 * Small helpers for the standard dialogs so controllers stay tidy.
 */
public final class AlertUtil {

    private AlertUtil() {
    }

    /** Shows an error dialog with a headline and the reason. */
    public static void error(String headline, String reason) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Paradox");
        alert.setHeaderText(headline);
        alert.setContentText(reason);
        alert.showAndWait();
    }

    /** Shows an information dialog. */
    public static void info(String headline, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Paradox");
        alert.setHeaderText(headline);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** Asks for confirmation; true when the user accepted. */
    public static boolean confirm(String headline, String question) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Paradox");
        alert.setHeaderText(headline);
        alert.setContentText(question);
        return alert.showAndWait().filter(t -> t == javafx.scene.control.ButtonType.OK).isPresent();
    }
}
