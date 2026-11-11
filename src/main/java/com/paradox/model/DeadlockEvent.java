package com.paradox.model;

import java.time.LocalDateTime;

/** A stored deadlock detection result, including its step-by-step trace. */
public class DeadlockEvent {

    private Long id;
    private Long simulationId;
    private DetectionType detectionType;
    private boolean deadlockDetected;
    private String deadlockedProcessesJson;   // JSON array of process names
    private String explanation;
    private String stepTraceJson;             // ordered JSON array of steps
    private LocalDateTime detectedAt;

    public DeadlockEvent() {
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

    public DetectionType getDetectionType() {
        return detectionType;
    }

    public void setDetectionType(DetectionType detectionType) {
        this.detectionType = detectionType;
    }

    public boolean isDeadlockDetected() {
        return deadlockDetected;
    }

    public void setDeadlockDetected(boolean deadlockDetected) {
        this.deadlockDetected = deadlockDetected;
    }

    public String getDeadlockedProcessesJson() {
        return deadlockedProcessesJson;
    }

    public void setDeadlockedProcessesJson(String deadlockedProcessesJson) {
        this.deadlockedProcessesJson = deadlockedProcessesJson;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getStepTraceJson() {
        return stepTraceJson;
    }

    public void setStepTraceJson(String stepTraceJson) {
        this.stepTraceJson = stepTraceJson;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(LocalDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }
}
