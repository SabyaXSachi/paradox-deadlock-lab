package com.paradox.controller;

import com.paradox.AppContext;
import com.paradox.model.Simulation;
import com.paradox.service.DashboardService;
import com.paradox.service.SimulationListener;

import java.time.format.DateTimeFormatter;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Dashboard: live statistics of the open simulation, global simulation
 * count, recent simulations and the latest log entries.
 */
public class DashboardController implements SimulationListener {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private Label totalSimulationsLabel;
    @FXML private Label totalProcessesLabel;
    @FXML private Label totalResourceTypesLabel;
    @FXML private Label allocatedInstancesLabel;
    @FXML private Label availableInstancesLabel;
    @FXML private Label systemStatusLabel;
    @FXML private TableView<Simulation> recentSimulationsTable;
    @FXML private TableColumn<Simulation, String> nameColumn;
    @FXML private TableColumn<Simulation, String> statusColumn;
    @FXML private TableColumn<Simulation, String> createdColumn;
    @FXML private ListView<String> recentEventsList;

    private final DashboardService dashboardService =
            new DashboardService(AppContext.engine());

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        createdColumn.setCellValueFactory(cellData -> {
            Simulation simulation = cellData.getValue();
            return new javafx.beans.property.SimpleStringProperty(
                    simulation.getCreatedAt() == null
                            ? "-"
                            : simulation.getCreatedAt().format(TIME_FORMAT));
        });
        recentEventsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        AppContext.engine().addListener(this);
        refresh();
    }

    private void refresh() {
        DashboardService.DashboardStats stats = dashboardService.collectStats();
        totalSimulationsLabel.setText(String.valueOf(stats.totalSimulations()));
        totalProcessesLabel.setText(String.valueOf(stats.processCount()));
        totalResourceTypesLabel.setText(String.valueOf(stats.resourceTypeCount()));
        allocatedInstancesLabel.setText(String.valueOf(stats.allocatedInstances()));
        availableInstancesLabel.setText(String.valueOf(stats.availableInstances()));
        systemStatusLabel.setText(stats.systemStatus());
        systemStatusLabel.getStyleClass().removeAll("status-ok", "status-deadlocked",
                "status-idle");
        if (!stats.simulationOpen()) {
            systemStatusLabel.getStyleClass().add("status-idle");
        } else if (stats.systemStatus().contains("DEADLOCK")) {
            systemStatusLabel.getStyleClass().add("status-deadlocked");
        } else {
            systemStatusLabel.getStyleClass().add("status-ok");
        }

        recentSimulationsTable.setItems(
                FXCollections.observableArrayList(dashboardService.recentSimulations(8)));
        recentEventsList.setItems(FXCollections.observableArrayList(
                dashboardService.recentEvents(30).stream()
                        .map(event -> "[" + event.getEventType() + "] " + event.getMessage())
                        .toList()));
    }

    @FXML
    private void loadDemo() {
        try {
            AppContext.demoScenarioService().loadCircularWaitDemo();
            com.paradox.util.AlertUtil.info("Demo scenario loaded",
                    "Three processes hold one resource each and request the next "
                            + "one: a circular wait. Open the Detection view and run "
                            + "detection, then resolve it in the Recovery view.");
        } catch (com.paradox.util.ValidationException e) {
            com.paradox.util.AlertUtil.error("Cannot load demo", e.getMessage());
        }
    }

    @Override
    public void onSimulationChanged(com.paradox.model.SimulationEvent event) {
        // Service calls happen on the FX thread, so a direct refresh is safe.
        javafx.application.Platform.runLater(this::refresh);
    }
}
