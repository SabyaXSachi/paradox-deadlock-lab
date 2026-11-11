package com.paradox.controller;

import com.paradox.AppContext;
import com.paradox.model.Allocation;
import com.paradox.model.ProcessState;
import com.paradox.model.Resource;
import com.paradox.model.SimProcess;
import com.paradox.service.SimulationListener;
import com.paradox.util.AlertUtil;
import com.paradox.util.ValidationException;

import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Process Manager: create processes with arrival time and priority, watch
 * their state change during the simulation, inspect their holdings and
 * terminate them.
 */
public class ProcessManagerController implements SimulationListener {

    @FXML private javafx.scene.control.TextField nameField;
    @FXML private Spinner<Integer> arrivalSpinner;
    @FXML private Spinner<Integer> prioritySpinner;
    @FXML private TableView<SimProcess> processTable;
    @FXML private TableColumn<SimProcess, String> nameColumn;
    @FXML private TableColumn<SimProcess, Integer> arrivalColumn;
    @FXML private TableColumn<SimProcess, Integer> priorityColumn;
    @FXML private TableColumn<SimProcess, String> stateColumn;
    @FXML private TableColumn<SimProcess, String> holdingsColumn;

    @FXML
    private void initialize() {
        arrivalSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 9999, 0));
        prioritySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 99, 0));

        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        arrivalColumn.setCellValueFactory(new PropertyValueFactory<>("arrivalTime"));
        priorityColumn.setCellValueFactory(new PropertyValueFactory<>("priority"));
        stateColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(
                        cellData.getValue().getState().name()));
        stateColumn.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                getStyleClass().removeAll("state-waiting", "state-blocked",
                        "state-terminated", "state-running");
                if (!empty && item != null) {
                    switch (item) {
                        case "WAITING" -> getStyleClass().add("state-waiting");
                        case "BLOCKED" -> getStyleClass().add("state-blocked");
                        case "TERMINATED" -> getStyleClass().add("state-terminated");
                        case "RUNNING" -> getStyleClass().add("state-running");
                        default -> {
                        }
                    }
                }
            }
        });
        holdingsColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(
                        describeHoldings(cellData.getValue())));

        AppContext.engine().addListener(this);
        refresh();
    }

    @FXML
    private void addProcess() {
        try {
            AppContext.processService().addProcess(
                    nameField.getText() == null ? "" : nameField.getText().trim(),
                    arrivalSpinner.getValue(),
                    prioritySpinner.getValue());
            nameField.clear();
            arrivalSpinner.getValueFactory().setValue(0);
            prioritySpinner.getValueFactory().setValue(0);
        } catch (ValidationException e) {
            AlertUtil.error("Cannot create process", e.getMessage());
        }
    }

    @FXML
    private void terminateSelected() {
        SimProcess selected = processTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.info("No process selected", "Select a process in the table first.");
            return;
        }
        if (selected.getState() == ProcessState.TERMINATED) {
            AlertUtil.info("Already terminated",
                    "Process " + selected.getName() + " is already terminated.");
            return;
        }
        if (!AlertUtil.confirm("Terminate process",
                "Terminate " + selected.getName() + "? All resources it holds are "
                        + "released and its pending requests are cancelled.")) {
            return;
        }
        try {
            AppContext.processService().terminateProcess(selected.getId());
        } catch (ValidationException e) {
            AlertUtil.error("Cannot terminate process", e.getMessage());
        }
    }

    private String describeHoldings(SimProcess process) {
        var holdings = AppContext.engine().allocationsOf(process.getId());
        if (holdings.isEmpty()) {
            return "-";
        }
        return holdings.stream()
                .map(allocation -> resourceName(allocation) + " x" + allocation.getInstances())
                .collect(Collectors.joining(", "));
    }

    private String resourceName(Allocation allocation) {
        return AppContext.engine().findResource(allocation.getResourceId())
                .map(Resource::getName).orElse("?");
    }

    private void refresh() {
        processTable.setItems(FXCollections.observableArrayList(
                AppContext.engine().getProcesses()));
        processTable.refresh();
    }

    @Override
    public void onSimulationChanged(com.paradox.model.SimulationEvent event) {
        javafx.application.Platform.runLater(this::refresh);
    }
}
