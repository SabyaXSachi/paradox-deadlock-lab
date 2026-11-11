package com.paradox.controller;

import com.paradox.AppContext;
import com.paradox.model.DeadlockEvent;
import com.paradox.model.DetectionType;
import com.paradox.model.RecoveryLog;
import com.paradox.model.Simulation;
import com.paradox.model.SimulationEvent;
import com.paradox.model.SimulationSnapshot;
import com.paradox.util.JsonParser;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Simulation History: everything MySQL stores about a simulation, including
 * step-by-step replay of the stored snapshots.
 */
public class HistoryController {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML private TableView<Simulation> simulationTable;
    @FXML private TableColumn<Simulation, String> simNameColumn;
    @FXML private TableColumn<Simulation, String> simStatusColumn;
    @FXML private TableColumn<Simulation, String> simCreatedColumn;
    @FXML private ListView<String> eventList;
    @FXML private ListView<String> deadlockList;
    @FXML private TableView<RecoveryLog> recoveryTable;
    @FXML private TableColumn<RecoveryLog, String> recStrategyColumn;
    @FXML private TableColumn<RecoveryLog, String> recTargetColumn;
    @FXML private TableColumn<RecoveryLog, Boolean> recSuccessColumn;
    @FXML private ListView<SimulationSnapshot> snapshotList;
    @FXML private TextArea replayArea;

    @FXML
    private void initialize() {
        simNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        simStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        simCreatedColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(
                        cellData.getValue().getCreatedAt() == null ? "-"
                                : cellData.getValue().getCreatedAt().format(TIME_FORMAT)));
        simulationTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> loadDetails(selected));

        eventList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        deadlockList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        snapshotList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(SimulationSnapshot snapshot, boolean empty) {
                super.updateItem(snapshot, empty);
                if (empty || snapshot == null) {
                    setText(null);
                } else {
                    setText("Step " + snapshot.getStepNumber()
                            + "   -   " + (snapshot.getCreatedAt() == null ? "-"
                            : snapshot.getCreatedAt().format(TIME_FORMAT)));
                }
            }
        });
        snapshotList.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> showSnapshot(selected));

        recStrategyColumn.setCellValueFactory(new PropertyValueFactory<>("strategy"));
        recTargetColumn.setCellValueFactory(new PropertyValueFactory<>("targetDescription"));
        recSuccessColumn.setCellValueFactory(new PropertyValueFactory<>("success"));

        refresh();
    }

    @FXML
    private void refresh() {
        Simulation selected = simulationTable.getSelectionModel().getSelectedItem();
        simulationTable.setItems(FXCollections.observableArrayList(
                AppContext.historyService().allSimulations()));
        if (selected != null) {
            simulationTable.getItems().stream()
                    .filter(s -> selected.getId() == s.getId())
                    .findFirst()
                    .ifPresent(s -> simulationTable.getSelectionModel().select(s));
        }
    }

    @FXML
    private void openSelectedInLaboratory() {
        Simulation selected = simulationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            com.paradox.util.AlertUtil.info("No simulation selected",
                    "Select a simulation in the table first.");
            return;
        }
        try {
            AppContext.simulationService().openSimulation(selected.getId());
            com.paradox.util.AlertUtil.info("Simulation opened",
                    "'" + selected.getName()
                            + "' is now the open simulation in the laboratory.");
        } catch (Exception e) {
            com.paradox.util.AlertUtil.error("Could not open simulation",
                    String.valueOf(e));
        }
    }

    private void loadDetails(Simulation simulation) {
        eventList.getItems().clear();
        deadlockList.getItems().clear();
        recoveryTable.getItems().clear();
        snapshotList.getItems().clear();
        replayArea.clear();
        if (simulation == null) {
            return;
        }
        long id = simulation.getId();

        List<SimulationEvent> events = AppContext.historyService().eventsOf(id);
        eventList.setItems(FXCollections.observableArrayList(events.stream()
                .map(event -> String.format("[%s] %s", event.getEventType(),
                        event.getMessage()))
                .toList()));

        List<DeadlockEvent> deadlockEvents = AppContext.historyService().deadlockEventsOf(id);
        deadlockList.setItems(FXCollections.observableArrayList(deadlockEvents.stream()
                .map(event -> String.format("%s: %s - %s",
                        event.getDetectionType() == DetectionType.RAG
                                ? "RAG cycle detection" : "Multi-instance detection",
                        event.isDeadlockDetected() ? "DEADLOCK" : "no deadlock",
                        event.getExplanation()))
                .toList()));

        recoveryTable.setItems(FXCollections.observableArrayList(
                AppContext.historyService().recoveryLogsOf(id)));

        snapshotList.setItems(FXCollections.observableArrayList(
                AppContext.historyService().snapshotsOf(id)));
    }

    private void showSnapshot(SimulationSnapshot snapshot) {
        if (snapshot == null) {
            replayArea.clear();
            return;
        }
        try {
            Map<?, ?> document = (Map<?, ?>) JsonParser.parse(snapshot.getSnapshotDataJson());
            replayArea.setText(formatSnapshot(snapshot, document));
        } catch (IllegalArgumentException e) {
            replayArea.setText("Snapshot could not be parsed: " + e.getMessage()
                    + "\n\nRaw JSON:\n" + snapshot.getSnapshotDataJson());
        }
    }

    /** Renders the stored JSON as a readable state report for the replay. */
    private String formatSnapshot(SimulationSnapshot snapshot, Map<?, ?> document) {
        StringBuilder sb = new StringBuilder();
        sb.append("Step ").append(snapshot.getStepNumber()).append("\n\n");

        sb.append("Processes:\n");
        for (Object item : listOf(document, "processes")) {
            Map<?, ?> process = (Map<?, ?>) item;
            sb.append(String.format("  %-8s state: %-10s arrival: %-4s priority: %s%n",
                    process.get("name"), process.get("state"),
                    process.get("arrival"), process.get("priority")));
        }

        sb.append("\nResources:\n");
        for (Object item : listOf(document, "resources")) {
            Map<?, ?> resource = (Map<?, ?>) item;
            sb.append(String.format("  %-8s total: %-4s available: %-4s allocated: %s%n",
                    resource.get("name"), resource.get("total"),
                    resource.get("available"), resource.get("allocated")));
        }

        sb.append("\nAllocations:\n");
        List<?> allocations = listOf(document, "allocations");
        if (allocations.isEmpty()) {
            sb.append("  (none)\n");
        }
        for (Object item : allocations) {
            List<?> row = (List<?>) item;
            sb.append(String.format("  %s holds %s x%s%n", row.get(0), row.get(1), row.get(2)));
        }

        sb.append("\nPending requests:\n");
        List<?> pending = listOf(document, "pendingRequests");
        if (pending.isEmpty()) {
            sb.append("  (none)\n");
        }
        for (Object item : pending) {
            List<?> row = (List<?>) item;
            sb.append(String.format("  %s waits for %s x%s%n",
                    row.get(0), row.get(1), row.get(2)));
        }
        return sb.toString();
    }

    private static List<?> listOf(Map<?, ?> document, String key) {
        Object raw = document.get(key);
        return raw instanceof List<?> list ? list : List.of();
    }
}
