package com.paradox.controller;

import com.paradox.AppContext;
import com.paradox.model.ProcessState;
import com.paradox.model.RecoveryLog;
import com.paradox.model.RecoveryResult;
import com.paradox.model.Resource;
import com.paradox.model.SimProcess;
import com.paradox.service.SimulationListener;
import com.paradox.util.AlertUtil;
import com.paradox.util.ValidationException;

import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

/**
 * Recovery Engine view: terminate one or all deadlocked processes, or
 * preempt resources (simulated). Every action is followed by a fresh
 * detection whose outcome is shown.
 */
public class RecoveryController implements SimulationListener {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML private ListView<Long> deadlockedList;
    @FXML private Label deadlockStatusLabel;
    @FXML private ComboBox<SimProcess> processCombo;
    @FXML private ComboBox<Resource> resourceCombo;
    @FXML private Spinner<Integer> instancesSpinner;
    @FXML private Label resultLabel;
    @FXML private TableView<RecoveryLog> logTable;
    @FXML private TableColumn<RecoveryLog, String> strategyColumn;
    @FXML private TableColumn<RecoveryLog, String> targetColumn;
    @FXML private TableColumn<RecoveryLog, Boolean> successColumn;
    @FXML private TableColumn<RecoveryLog, String> timeColumn;

    @FXML
    private void initialize() {
        instancesSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1));
        processCombo.setConverter(processConverter());
        resourceCombo.setConverter(resourceConverter());
        strategyColumn.setCellValueFactory(new PropertyValueFactory<>("strategy"));
        targetColumn.setCellValueFactory(new PropertyValueFactory<>("targetDescription"));
        successColumn.setCellValueFactory(new PropertyValueFactory<>("success"));
        timeColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(
                        cellData.getValue().getExecutedAt() == null ? "-"
                                : cellData.getValue().getExecutedAt().format(TIME_FORMAT)));
        AppContext.engine().addListener(this);
        refresh();
    }

    @FXML
    private void detectNow() {
        try {
            AppContext.detectionService().detect();
            refresh();
        } catch (ValidationException e) {
            AlertUtil.error("Cannot run detection", e.getMessage());
        }
    }

    @FXML
    private void terminateSelected() {
        Long selected = deadlockedList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.info("No process selected",
                    "Select a deadlocked process in the list first (run detection).");
            return;
        }
        if (!AlertUtil.confirm("Terminate process",
                "Terminate the selected process and release all of its resources?")) {
            return;
        }
        try {
            RecoveryResult result = AppContext.recoveryService().terminateOne(selected);
            showResult(result);
        } catch (ValidationException e) {
            AlertUtil.error("Cannot terminate process", e.getMessage());
        }
    }

    @FXML
    private void terminateAll() {
        if (!AlertUtil.confirm("Terminate all deadlocked processes",
                "This terminates every deadlocked process and releases their resources.")) {
            return;
        }
        try {
            RecoveryResult result = AppContext.recoveryService().terminateAllDeadlocked();
            showResult(result);
        } catch (ValidationException e) {
            AlertUtil.error("Cannot terminate processes", e.getMessage());
        }
    }

    @FXML
    private void preemptSelected() {
        SimProcess process = processCombo.getValue();
        Resource resource = resourceCombo.getValue();
        if (process == null || resource == null) {
            AlertUtil.info("Selection incomplete", "Choose a process and a resource first.");
            return;
        }
        try {
            RecoveryResult result = AppContext.recoveryService().preempt(
                    process.getId(), resource.getId(), instancesSpinner.getValue());
            showResult(result);
        } catch (ValidationException e) {
            AlertUtil.error("Cannot preempt resource", e.getMessage());
        }
    }

    private void showResult(RecoveryResult result) {
        resultLabel.setText((result.isSuccess() ? "RESOLVED: " : "STILL DEADLOCKED: ")
                + result.getSummary());
        refresh();
    }

    // ---------- display refresh ----------

    private void refresh() {
        List<Long> deadlocked = AppContext.getLastDetectionResult() == null
                ? List.of()
                : AppContext.getLastDetectionResult().getDeadlockedProcessIds();
        boolean deadlockPresent = AppContext.getLastDetectionResult() != null
                && AppContext.getLastDetectionResult().isDeadlockDetected();

        deadlockedList.setItems(FXCollections.observableArrayList(deadlocked));
        deadlockStatusLabel.setText(deadlockPresent
                ? "Deadlock present - recovery needed"
                : "No deadlock on record (run detection to check)");
        deadlockStatusLabel.getStyleClass().removeAll("status-ok", "status-deadlocked");
        deadlockStatusLabel.getStyleClass().add(deadlockPresent
                ? "status-deadlocked" : "status-ok");

        processCombo.setItems(FXCollections.observableArrayList(
                AppContext.engine().getProcesses().stream()
                        .filter(p -> p.getState() != ProcessState.TERMINATED)
                        .toList()));
        resourceCombo.setItems(FXCollections.observableArrayList(
                AppContext.engine().getResources()));

        long simulationId = AppContext.engine().hasSimulation()
                ? AppContext.engine().getSimulation().getId() : -1;
        logTable.setItems(FXCollections.observableArrayList(simulationId > 0
                ? AppContext.historyService().recoveryLogsOf(simulationId)
                : List.of()));
    }

    private StringConverter<SimProcess> processConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(SimProcess process) {
                return process == null ? "" : process.getName()
                        + "  [" + process.getState() + "]";
            }

            @Override
            public SimProcess fromString(String string) {
                return null; // read-only combo
            }
        };
    }

    private StringConverter<Resource> resourceConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Resource resource) {
                return resource == null ? "" : resource.getName()
                        + " (available " + resource.getAvailableInstances() + ")";
            }

            @Override
            public Resource fromString(String string) {
                return null; // read-only combo
            }
        };
    }

    @Override
    public void onSimulationChanged(com.paradox.model.SimulationEvent event) {
        javafx.application.Platform.runLater(this::refresh);
    }
}
