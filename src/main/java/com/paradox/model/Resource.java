package com.paradox.model;

/**
 * A resource type with a fixed number of identical instances.
 * The invariant availableInstances + allocatedInstances == totalInstances
 * is protected in the service layer and re-checked by the database.
 */
public class Resource {

    private Long id;
    private Long simulationId;
    private String name;      // R1, R2, ...
    private int totalInstances;
    private int availableInstances;
    private int allocatedInstances;

    public Resource() {
    }

    public Resource(Long simulationId, String name, int totalInstances) {
        this.simulationId = simulationId;
        this.name = name;
        this.totalInstances = totalInstances;
        this.availableInstances = totalInstances;
        this.allocatedInstances = 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(Long simulationId) {
        this.simulationId = simulationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTotalInstances() {
        return totalInstances;
    }

    public void setTotalInstances(int totalInstances) {
        this.totalInstances = totalInstances;
    }

    public int getAvailableInstances() {
        return availableInstances;
    }

    public void setAvailableInstances(int availableInstances) {
        this.availableInstances = availableInstances;
    }

    public int getAllocatedInstances() {
        return allocatedInstances;
    }

    public void setAllocatedInstances(int allocatedInstances) {
        this.allocatedInstances = allocatedInstances;
    }
}
