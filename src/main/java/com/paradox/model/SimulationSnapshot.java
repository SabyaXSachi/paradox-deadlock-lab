package com.paradox.model;

import java.time.LocalDateTime;

/** A full-state snapshot of a simulation at one step, stored as JSON. */
public class SimulationSnapshot {

    private Long id;
    private Long simulationId;
    private int stepNumber;
    private String snapshotDataJson;
    private LocalDateTime createdAt;

    public SimulationSnapshot() {
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

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getSnapshotDataJson() {
        return snapshotDataJson;
    }

    public void setSnapshotDataJson(String snapshotDataJson) {
        this.snapshotDataJson = snapshotDataJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
