package com.paradox.controller;

import com.paradox.AppContext;
import com.paradox.algorithm.BankersResult;
import com.paradox.model.ProcessState;
import com.paradox.model.Resource;
import com.paradox.model.SimProcess;
import com.paradox.service.SimulationListener;
import com.paradox.util.AlertUtil;
import com.paradox.util.ValidationException;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;

/**
 * Banker's Algorithm view: maintain the maximum-claim matrix, run the
 * safety algorithm and evaluate requests. Safe/unsafe is clearly kept
 * apart from actual deadlock detection.
 */
public class BankersController implements SimulationListener {

    @FXML private ComboBox<SimProcess> processCombo;
    @FXML private ComboBox<Resource> resourceCombo;
    @FXML private Spinner<Integer> claimSpinner;
    @FXML private TableView<MaxRow> claimTable;
    @FXML private Label verdictLabel;
    @FXML private Label messageLabel;
    @FXML private Label sequenceLabel;
    @FXML private ListView<String> stepsList;

    @FXML
    private void initialize() {
        claimSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 999, 0));
        processCombo.setConverter(processConverter());
        resourceCombo.setConverter(resourceConverter());
        AppContext.engine().addListener(this);
        refresh();
        clearResult();
    }

    @FXML
    private void setClaim() {
        SimProcess process = processCombo.getValue();
        Resource resource = resourceCombo.getValue();
        if (process == null || resource == null) {
            AlertUtil.info("Selection incomplete", "Choose a process and a resource first.");
            return;
        }
        try {
            AppContext.bankersService().setMaxClaim(process.getId(), resource.getId(),
                    claimSpinner.getValue());
        } catch (ValidationException e) {
            AlertUtil.error("Cannot set maximum claim", e.getMessage());
        }
    }

    @FXML
    private void runSafetyCheck() {
        try {
            BankersResult result = AppContext.bankersService().runSafetyCheck();
            showResult(result.isSafe() ? "SAFE STATE" : "UNSAFE STATE",
                    result.isSafe() ? "status-ok" : "status-deadlocked",
                    result.getMessage(), result);
        } catch (ValidationException e) {
            AlertUtil.error("Cannot run safety check", e.getMessage());
        }
    }

    @FXML
    private void evaluateRequest() {
        SimProcess process = processCombo.getValue();
        Resource resource = resourceCombo.getValue();
        if (process == null || resource == null) {
            AlertUtil.info("Selection incomplete", "Choose a process and a resource first.");
            return;
        }
        try {
            BankersResult result = AppContext.bankersService().evaluateRequest(
                    process.getId(), resource.getId(), claimSpinner.getValue());
            String verdict = switch (result.getDecision()) {
                case GRANT -> "REQUEST CAN BE GRANTED";
                case WAIT_UNSAFE -> "REQUEST MUST WAIT (unsafe)";
                case WAIT_NO_INSTANCES -> "REQUEST MUST WAIT (no instances)";
                case DENY_EXCEEDS_MAXIMUM -> "REQUEST DENIED (exceeds claim)";
            };
            String style = result.getDecision() == BankersResult.Decision.GRANT
                    ? "status-ok" : "status-deadlocked";
            showResult(verdict, style, result.getMessage(), result);
        } catch (ValidationException e) {
            AlertUtil.error("Cannot evaluate request", e.getMessage());
        }
    }

    private void showResult(String verdict, String style, String message,
                            BankersResult result) {
        verdictLabel.setText(verdict);
        verdictLabel.getStyleClass().removeAll("status-ok", "status-deadlocked");
        verdictLabel.getStyleClass().add(style);
        messageLabel.setText(message);
        StringBuilder sequence = new StringBuilder("Safe sequence: ");
        if (result.getSafeSequence() == null || result.getSafeSequence().isEmpty()) {
            sequence.append("none");
        } else {
            for (int i = 0; i < result.getSafeSequence().size(); i++) {
                if (i > 0) {
                    sequence.append(" -> ");
                }
                sequence.append('P').append(result.getSafeSequence().get(i));
            }
        }
        sequenceLabel.setText(sequence.toString());
        stepsList.setItems(FXCollections.observableArrayList(
                result.getSteps().stream().map(Object::toString).toList()));
    }

    private void clearResult() {
        verdictLabel.setText("Not checked yet");
        messageLabel.setText("Banker's Algorithm avoids deadlocks by only granting "
                + "requests that keep the state safe. An unsafe state is a risk - "
                + "it is not a detected deadlock.");
        sequenceLabel.setText("Safe sequence: -");
    }

    // ---------- display refresh ----------

    private void refresh() {
        SimProcess selectedProcess = processCombo.getValue();
        Resource selectedResource = resourceCombo.getValue();
        processCombo.setItems(FXCollections.observableArrayList(
                AppContext.engine().getProcesses().stream()
                        .filter(p -> p.getState() != ProcessState.TERMINATED)
                        .toList()));
        resourceCombo.setItems(FXCollections.observableArrayList(
                AppContext.engine().getResources()));
        restoreSelection(processCombo, selectedProcess);
        restoreSelection(resourceCombo, selectedResource);
        rebuildClaimTable();
    }

    private static <T> void restoreSelection(ComboBox<T> combo, T previous) {
        if (previous == null) {
            return;
        }
        combo.getItems().stream()
                .filter(item -> item == previous)
                .findFirst()
                .ifPresent(combo.getSelectionModel()::select);
    }

    private void rebuildClaimTable() {
        claimTable.getColumns().clear();
        var activeProcesses = AppContext.engine().getProcesses().stream()
                .filter(p -> p.getState() != ProcessState.TERMINATED)
                .toList();

        TableColumn<MaxRow, String> nameColumn = new TableColumn<>("Process");
        nameColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getProcessName()));
        nameColumn.setPrefWidth(100);
        claimTable.getColumns().add(nameColumn);

        for (Resource resource : AppContext.engine().getResources()) {
            TableColumn<MaxRow, String> column = new TableColumn<>(resource.getName());
            long resourceId = resource.getId();
            column.setCellValueFactory(data -> new SimpleStringProperty(
                    data.getValue().claimFor(resourceId)));
            column.setPrefWidth(90);
            claimTable.getColumns().add(column);
        }
        claimTable.setItems(FXCollections.observableArrayList(
                activeProcesses.stream().map(MaxRow::new).toList()));
    }

    /** Row of the maximum-claim matrix. */
    public static class MaxRow {
        private final SimProcess process;

        MaxRow(SimProcess process) {
            this.process = process;
        }

        public String getProcessName() {
            return process.getName();
        }

        public String claimFor(long resourceId) {
            Integer claim = AppContext.engine().getMaxClaims()
                    .getOrDefault(process.getId(), java.util.Map.of())
                    .get(resourceId);
            return claim == null ? "-" : String.valueOf(claim);
        }
    }

    // ---------- converters ----------

    private StringConverter<SimProcess> processConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(SimProcess process) {
                return process == null ? "" : process.getName();
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
                        + " (total " + resource.getTotalInstances() + ")";
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
