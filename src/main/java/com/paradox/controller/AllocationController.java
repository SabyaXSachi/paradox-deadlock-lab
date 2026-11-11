package com.paradox.controller;

import com.paradox.AppContext;
import com.paradox.model.Allocation;
import com.paradox.model.Resource;
import com.paradox.model.ResourceRequest;
import com.paradox.model.SimProcess;
import com.paradox.service.SimulationListener;
import com.paradox.util.AlertUtil;
import com.paradox.util.ValidationException;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

/**
 * Resource Allocation Engine: direct allocation for setting up the initial
 * state, requests that either get granted or make the process wait, and
 * releases that may wake waiting processes.
 */
public class AllocationController implements SimulationListener {

    @FXML private ComboBox<SimProcess> processCombo;
    @FXML private ComboBox<Resource> resourceCombo;
    @FXML private Spinner<Integer> instancesSpinner;
    @FXML private Button allocateButton;
    @FXML private Button requestButton;
    @FXML private Button releaseButton;
    @FXML private TableView<AllocationRow> allocationTable;
    @FXML private TableColumn<AllocationRow, String> allocProcessColumn;
    @FXML private TableColumn<AllocationRow, String> allocResourceColumn;
    @FXML private TableColumn<AllocationRow, Integer> allocInstancesColumn;
    @FXML private TableView<RequestRow> requestTable;
    @FXML private TableColumn<RequestRow, String> requestProcessColumn;
    @FXML private TableColumn<RequestRow, String> requestResourceColumn;
    @FXML private TableColumn<RequestRow, Integer> requestInstancesColumn;
    @FXML private TableColumn<RequestRow, String> requestStatusColumn;

    @FXML
    private void initialize() {
        instancesSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1));

        processCombo.setConverter(processConverter());
        resourceCombo.setConverter(resourceConverter());

        allocProcessColumn.setCellValueFactory(new PropertyValueFactory<>("process"));
        allocResourceColumn.setCellValueFactory(new PropertyValueFactory<>("resource"));
        allocInstancesColumn.setCellValueFactory(new PropertyValueFactory<>("instances"));
        requestProcessColumn.setCellValueFactory(new PropertyValueFactory<>("process"));
        requestResourceColumn.setCellValueFactory(new PropertyValueFactory<>("resource"));
        requestInstancesColumn.setCellValueFactory(new PropertyValueFactory<>("instances"));
        requestStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        AppContext.engine().addListener(this);
        refresh();
    }

    @FXML
    private void allocate() {
        runOperation(Operation.ALLOCATE);
    }

    @FXML
    private void request() {
        runOperation(Operation.REQUEST);
    }

    @FXML
    private void release() {
        runOperation(Operation.RELEASE);
    }

    @FXML
    private void cancelSelectedRequest() {
        RequestRow selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.info("No request selected", "Select a pending request first.");
            return;
        }
        try {
            AppContext.allocationService().cancelRequest(selected.request.getId());
        } catch (ValidationException e) {
            AlertUtil.error("Cannot cancel request", e.getMessage());
        }
    }

    private enum Operation { ALLOCATE, REQUEST, RELEASE }

    private void runOperation(Operation operation) {
        SimProcess process = processCombo.getValue();
        Resource resource = resourceCombo.getValue();
        if (process == null || resource == null) {
            AlertUtil.info("Selection incomplete",
                    "Choose a process and a resource first.");
            return;
        }
        int instances = instancesSpinner.getValue();
        try {
            switch (operation) {
                case ALLOCATE ->
                        AppContext.allocationService().allocate(
                                process.getId(), resource.getId(), instances);
                case REQUEST ->
                        AppContext.allocationService().request(
                                process.getId(), resource.getId(), instances);
                case RELEASE ->
                        AppContext.allocationService().release(
                                process.getId(), resource.getId(), instances);
            }
        } catch (ValidationException e) {
            AlertUtil.error("Operation rejected", e.getMessage());
        }
    }

    // ---------- display helpers ----------

    private void refresh() {
        SimProcess selectedProcess = processCombo.getValue();
        Resource selectedResource = resourceCombo.getValue();

        processCombo.setItems(FXCollections.observableArrayList(
                AppContext.engine().getProcesses()));
        resourceCombo.setItems(FXCollections.observableArrayList(
                AppContext.engine().getResources()));
        restoreSelection(processCombo, selectedProcess);
        restoreSelection(resourceCombo, selectedResource);

        allocationTable.setItems(FXCollections.observableArrayList(
                AppContext.engine().getAllocations().stream()
                        .map(AllocationRow::new).toList()));
        requestTable.setItems(FXCollections.observableArrayList(
                AppContext.engine().getAllRequests().stream()
                        .filter(r -> r.getStatus()
                                == com.paradox.model.RequestStatus.PENDING)
                        .map(RequestRow::new).toList()));
        boolean engineReady = AppContext.engine().hasSimulation();
        allocateButton.setDisable(!engineReady);
        requestButton.setDisable(!engineReady);
        releaseButton.setDisable(!engineReady);
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
                        + "  (available " + resource.getAvailableInstances()
                        + "/" + resource.getTotalInstances() + ")";
            }

            @Override
            public Resource fromString(String string) {
                return null; // read-only combo
            }
        };
    }

    /** Display row for the allocations table. */
    public static class AllocationRow {
        private final Allocation allocation;

        AllocationRow(Allocation allocation) {
            this.allocation = allocation;
        }

        public String getProcess() {
            return AppContext.engine().findProcess(allocation.getProcessId())
                    .map(SimProcess::getName).orElse("?");
        }

        public String getResource() {
            return AppContext.engine().findResource(allocation.getResourceId())
                    .map(Resource::getName).orElse("?");
        }

        public int getInstances() {
            return allocation.getInstances();
        }
    }

    /** Display row for the pending requests table. */
    public static class RequestRow {
        private final ResourceRequest request;

        RequestRow(ResourceRequest request) {
            this.request = request;
        }

        public String getProcess() {
            return AppContext.engine().findProcess(request.getProcessId())
                    .map(SimProcess::getName).orElse("?");
        }

        public String getResource() {
            return AppContext.engine().findResource(request.getResourceId())
                    .map(Resource::getName).orElse("?");
        }

        public int getInstances() {
            return request.getInstances();
        }

        public String getStatus() {
            return request.getStatus().name();
        }
    }

    @Override
    public void onSimulationChanged(com.paradox.model.SimulationEvent event) {
        javafx.application.Platform.runLater(this::refresh);
    }
}
