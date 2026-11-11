package com.paradox.controller;

import com.paradox.AppContext;
import com.paradox.model.Simulation;
import com.paradox.service.SimulationListener;
import com.paradox.util.AlertUtil;
import com.paradox.util.ValidationException;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Application shell: header with simulation control, sidebar navigation and
 * the content pane that hosts the module views. Views are loaded once and
 * cached; every controller refreshes itself through engine events.
 */
public class MainController implements SimulationListener {

    @FXML private StackPane contentPane;
    @FXML private ComboBox<Simulation> simulationCombo;
    @FXML private Label openSimulationLabel;
    @FXML private Button dashboardButton;
    @FXML private Button processesButton;
    @FXML private Button resourcesButton;
    @FXML private Button allocationButton;
    @FXML private Button detectionButton;
    @FXML private Button bankersButton;
    @FXML private Button visualizerButton;
    @FXML private Button recoveryButton;
    @FXML private Button historyButton;

    private final Map<String, Node> viewCache = new HashMap<>();
    private final Map<String, String> viewTitles = new HashMap<>();
    private String activeView = "dashboard";

    @FXML
    private void initialize() {
        viewTitles.put("dashboard", "Dashboard");
        viewTitles.put("processes", "Process Manager");
        viewTitles.put("resources", "Resource Manager");
        viewTitles.put("allocation", "Resource Allocation Engine");
        viewTitles.put("detection", "Deadlock Detection Engine");
        viewTitles.put("bankers", "Banker's Algorithm");
        viewTitles.put("visualizer", "Deadlock Visualizer");
        viewTitles.put("recovery", "Recovery Engine");
        viewTitles.put("history", "Simulation History");

        simulationCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Simulation simulation) {
                if (simulation == null) {
                    return "";
                }
                return simulation.getName() + "  (" + simulation.getStatus() + ")";
            }

            @Override
            public Simulation fromString(String string) {
                return null; // read-only combo
            }
        });

        AppContext.engine().addListener(this);
        if (!AppContext.isDatabaseReady()) {
            AlertUtil.error("Database not reachable",
                    "MySQL is not running or the connection settings in "
                            + "config/database.properties are wrong. The UI will start, "
                            + "but persistence is disabled until the database is available. "
                            + "See README.md for the database setup.");
        }
        refreshSimulationCombo();
        showView("dashboard");
    }

    // ---------- navigation ----------

    @FXML
    private void openDashboard() {
        showView("dashboard");
    }

    @FXML
    private void openProcesses() {
        showView("processes");
    }

    @FXML
    private void openResources() {
        showView("resources");
    }

    @FXML
    private void openAllocation() {
        showView("allocation");
    }

    @FXML
    private void openDetection() {
        showView("detection");
    }

    @FXML
    private void openBankers() {
        showView("bankers");
    }

    @FXML
    private void openVisualizer() {
        showView("visualizer");
    }

    @FXML
    private void openRecovery() {
        showView("recovery");
    }

    @FXML
    private void openHistory() {
        showView("history");
    }

    private void showView(String viewName) {
        activeView = viewName;
        highlightActiveButton(viewName);
        Node view = viewCache.computeIfAbsent(viewName, this::loadView);
        contentPane.getChildren().setAll(view);
    }

    private Node loadView(String viewName) {
        URL fxml = getClass().getResource("/fxml/" + viewName + ".fxml");
        if (fxml == null) {
            // Modules arriving in later phases get an informative placeholder.
            return placeholderFor(viewName);
        }
        try {
            return FXMLLoader.load(fxml);
        } catch (IOException e) {
            AlertUtil.error("Could not open view", viewName + ": " + e.getMessage());
            return placeholderFor(viewName);
        }
    }

    private Node placeholderFor(String viewName) {
        Label title = new Label(viewTitles.getOrDefault(viewName, viewName));
        title.getStyleClass().add("welcome-title");
        Label note = new Label("This module is implemented in a later development phase.");
        note.getStyleClass().add("welcome-note");
        VBox box = new VBox(10, title, note);
        box.getStyleClass().add("welcome-pane");
        box.setAlignment(javafx.geometry.Pos.CENTER);
        return box;
    }

    private void highlightActiveButton(String viewName) {
        Map<String, Button> buttons = Map.of(
                "dashboard", dashboardButton, "processes", processesButton,
                "resources", resourcesButton, "allocation", allocationButton,
                "detection", detectionButton, "bankers", bankersButton,
                "visualizer", visualizerButton, "recovery", recoveryButton,
                "history", historyButton);
        buttons.forEach((name, button) -> {
            button.getStyleClass().remove("nav-button-active");
            if (name.equals(viewName)) {
                button.getStyleClass().add("nav-button-active");
            }
        });
    }

    // ---------- simulation control (header) ----------

    @FXML
    private void createSimulation() {
        Dialog<Simulation> dialog = buildNewSimulationDialog();
        Optional<Simulation> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }
        try {
            AppContext.simulationService().createSimulation(
                    result.get().getName(), result.get().getDescription());
            refreshSimulationCombo();
        } catch (ValidationException e) {
            AlertUtil.error("Cannot create simulation", e.getMessage());
        }
    }

    @FXML
    private void openSelectedSimulation() {
        Simulation selected = simulationCombo.getValue();
        if (selected == null) {
            AlertUtil.info("No simulation selected",
                    "Choose a saved simulation from the list first.");
            return;
        }
        // Loading a full simulation touches many tables; keep the UI responsive.
        Task<Simulation> task = new Task<>() {
            @Override
            protected Simulation call() {
                return AppContext.simulationService().openSimulation(selected.getId());
            }
        };
        task.setOnSucceeded(e -> {
            refreshSimulationCombo();
            openSimulationLabel.setText(selected.getName() + " - "
                    + selected.getStatus());
        });
        task.setOnFailed(e -> AlertUtil.error("Could not open simulation",
                String.valueOf(task.getException())));
        new Thread(task, "open-simulation").start();
    }

    @FXML
    private void deleteSelectedSimulation() {
        Simulation selected = simulationCombo.getValue();
        if (selected == null) {
            AlertUtil.info("No simulation selected",
                    "Choose a saved simulation from the list first.");
            return;
        }
        if (!AlertUtil.confirm("Delete simulation",
                "Delete '" + selected.getName() + "' with all of its processes, "
                        + "resources and logs? This cannot be undone.")) {
            return;
        }
        try {
            AppContext.simulationService().deleteSimulation(selected.getId());
            refreshSimulationCombo();
            openSimulationLabel.setText("No simulation open");
        } catch (Exception e) {
            AlertUtil.error("Could not delete simulation", String.valueOf(e));
        }
    }

    private Dialog<Simulation> buildNewSimulationDialog() {
        Dialog<Simulation> dialog = new Dialog<>();
        dialog.setTitle("New simulation");
        dialog.setHeaderText("Create a new deadlock laboratory session");

        ButtonType createType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Circular Wait Demo");
        TextField descriptionField = new TextField();
        descriptionField.setPromptText("optional description");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Name"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Description"), 0, 1);
        grid.add(descriptionField, 1, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.getDialogPane().lookupButton(createType).disableProperty()
                .bind(nameField.textProperty().isEmpty());

        dialog.setResultConverter(button ->
                button == createType
                        ? new Simulation(nameField.getText(), descriptionField.getText())
                        : null);
        return dialog;
    }

    private void refreshSimulationCombo() {
        List<Simulation> simulations =
                AppContext.simulationService().listRecent(100);
        simulationCombo.setItems(FXCollections.observableArrayList(simulations));
        Simulation open = AppContext.engine().getSimulation();
        if (open != null) {
            simulationCombo.getItems().stream()
                    .filter(s -> s.getId() == open.getId())
                    .findFirst()
                    .ifPresent(simulationCombo.getSelectionModel()::select);
        }
    }

    // ---------- engine listener ----------

    @Override
    public void onSimulationChanged(com.paradox.model.SimulationEvent event) {
        // Header always mirrors the live state; the visible view refreshes itself.
        if (AppContext.engine().hasSimulation()) {
            Simulation simulation = AppContext.engine().getSimulation();
            openSimulationLabel.setText(simulation.getName() + " - " + simulation.getStatus());
            refreshSimulationCombo();
        } else {
            openSimulationLabel.setText("No simulation open");
        }
    }
}
