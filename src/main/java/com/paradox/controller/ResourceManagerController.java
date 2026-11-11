package com.paradox.controller;

import com.paradox.AppContext;
import com.paradox.model.Resource;
import com.paradox.service.SimulationListener;
import com.paradox.util.AlertUtil;
import com.paradox.util.ValidationException;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Resource Manager: create resource types with their total instance count
 * and delete them when they are safe to remove.
 */
public class ResourceManagerController implements SimulationListener {

    @FXML private TextField nameField;
    @FXML private Spinner<Integer> instancesSpinner;
    @FXML private TableView<Resource> resourceTable;
    @FXML private TableColumn<Resource, String> nameColumn;
    @FXML private TableColumn<Resource, Integer> totalColumn;
    @FXML private TableColumn<Resource, Integer> availableColumn;
    @FXML private TableColumn<Resource, Integer> allocatedColumn;

    @FXML
    private void initialize() {
        instancesSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1));

        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        totalColumn.setCellValueFactory(new PropertyValueFactory<>("totalInstances"));
        availableColumn.setCellValueFactory(new PropertyValueFactory<>("availableInstances"));
        allocatedColumn.setCellValueFactory(new PropertyValueFactory<>("allocatedInstances"));

        AppContext.engine().addListener(this);
        refresh();
    }

    @FXML
    private void addResource() {
        try {
            AppContext.resourceService().addResource(
                    nameField.getText() == null ? "" : nameField.getText().trim(),
                    instancesSpinner.getValue());
            nameField.clear();
            instancesSpinner.getValueFactory().setValue(1);
        } catch (ValidationException e) {
            AlertUtil.error("Cannot create resource", e.getMessage());
        }
    }

    @FXML
    private void deleteSelected() {
        Resource selected = resourceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.info("No resource selected", "Select a resource in the table first.");
            return;
        }
        if (!AlertUtil.confirm("Delete resource",
                "Delete resource " + selected.getName() + "?")) {
            return;
        }
        try {
            AppContext.resourceService().deleteResource(selected.getId());
        } catch (ValidationException e) {
            AlertUtil.error("Cannot delete resource", e.getMessage());
        }
    }

    private void refresh() {
        resourceTable.setItems(FXCollections.observableArrayList(
                AppContext.engine().getResources()));
        resourceTable.refresh();
    }

    @Override
    public void onSimulationChanged(com.paradox.model.SimulationEvent event) {
        javafx.application.Platform.runLater(this::refresh);
    }
}
