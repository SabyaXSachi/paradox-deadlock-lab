package com.paradox.model;

import java.time.LocalDateTime;

/** A stored recovery operation and its outcome. */
public class RecoveryLog {

    private Long id;
    private Long simulationId;
    private RecoveryStrategy strategy;
    private String targetDescription;
    private String resultSummary;
    private boolean success;
    private LocalDateTime executedAt;

    public RecoveryLog() {
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

    public RecoveryStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(RecoveryStrategy strategy) {
        this.strategy = strategy;
    }

    public String getTargetDescription() {
        return targetDescription;
    }

    public void setTargetDescription(String targetDescription) {
        this.targetDescription = targetDescription;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }
}
