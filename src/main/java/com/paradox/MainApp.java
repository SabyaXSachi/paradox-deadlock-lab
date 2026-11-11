package com.paradox;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Paradox - Deadlock Detection and Recovery Laboratory.
 *
 * Application entry point. Initializes the application context (database
 * check, schema), then loads the main layout and applies the global stylesheet.
 */
public class MainApp extends Application {

    private static final int WINDOW_WIDTH = 1180;
    private static final int WINDOW_HEIGHT = 720;

    @Override
    public void start(Stage stage) throws IOException {
        AppContext.initialize();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/css/paradox.css").toExternalForm());

        stage.setTitle("Paradox - Deadlock Detection and Recovery Laboratory");
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
