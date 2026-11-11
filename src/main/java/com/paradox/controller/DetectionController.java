package com.paradox.controller;

import com.paradox.AppContext;
import com.paradox.algorithm.DetectionResult;
import com.paradox.model.Resource;
import com.paradox.model.SimProcess;
import com.paradox.service.SimulationListener;
import com.paradox.util.AlertUtil;
import com.paradox.util.ValidationException;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

/**
 * Deadlock Detection Engine view: runs the appropriate algorithm on the
 * live state and shows the verdict, the involved processes and the full
 * step-by-step trace.
 */
public class DetectionController implements SimulationListener {

    @FXML private Label algorithmLabel;
    @FXML private Label verdictLabel;
    @FXML private Label explanationLabel;
    @FXML private Label workVectorLabel;
    @FXML private ListView<Long> deadlockedList;
    @FXML private ListView<Long> finishedList;
    @FXML private ListView<String> stepsList;

    @FXML
    private void initialize() {
        deadlockedList.setCellFactory(list -> processCell());
        finishedList.setCellFactory(list -> processCell());
        AppContext.engine().addListener(this);
        clearResult();
    }

    @FXML
    private void runDetection() {
        try {
            DetectionResult result = AppContext.detectionService().detect();
            showResult(result);
        } catch (ValidationException e) {
            AlertUtil.error("Cannot run detection", e.getMessage());
        }
    }

    private void showResult(DetectionResult result) {
        verdictLabel.setText(result.isDeadlockDetected()
                ? "DEADLOCK DETECTED"
                : "NO DEADLOCK");
        verdictLabel.getStyleClass().removeAll("status-ok", "status-deadlocked");
        verdictLabel.getStyleClass().add(result.isDeadlockDetected()
                ? "status-deadlocked" : "status-ok");

        explanationLabel.setText(result.getExplanation());
        workVectorLabel.setText("Final Work vector: " + vectorText(result));

        deadlockedList.setItems(javafx.collections.FXCollections
                .observableArrayList(result.getDeadlockedProcessIds()));
        finishedList.setItems(javafx.collections.FXCollections
                .observableArrayList(result.getFinishedProcessIds()));
        stepsList.setItems(javafx.collections.FXCollections.observableArrayList(
                result.getSteps().stream().map(Object::toString).toList()));
    }

    private String vectorText(DetectionResult result) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < result.getFinalWorkVector().length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(result.getFinalWorkVector()[i]);
        }
        return sb.append(')').toString();
    }

    private ListCell<Long> processCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Long processId, boolean empty) {
                super.updateItem(processId, empty);
                if (empty || processId == null) {
                    setText(null);
                } else {
                    String name = AppContext.engine().findProcess(processId)
                            .map(SimProcess::getName).orElse("P" + processId);
                    setText(name + "  (id " + processId + ")");
                }
            }
        };
    }

    private void clearResult() {
        verdictLabel.setText("Not checked yet");
        verdictLabel.getStyleClass().removeAll("status-ok", "status-deadlocked");
        explanationLabel.setText("Run a detection to analyze the current state. With one "
                + "instance per resource type the RAG cycle detection is used; with "
                + "multiple instances the Work/Finish algorithm decides.");
        deadlockedList.getItems().clear();
        finishedList.getItems().clear();
        stepsList.getItems().clear();
    }

    @Override
    public void onSimulationChanged(com.paradox.model.SimulationEvent event) {
        javafx.application.Platform.runLater(() -> {
            algorithmLabel.setText(AppContext.engine().getResources().stream()
                    .allMatch(r -> r.getTotalInstances() == 1)
                    ? "Algorithm on next run: RAG cycle detection (single-instance)"
                    : "Algorithm on next run: Work/Finish (multi-instance)");
        });
    }
}
