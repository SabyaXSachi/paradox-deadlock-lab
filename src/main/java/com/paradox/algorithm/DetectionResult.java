package com.paradox.algorithm;

import java.util.List;

/**
 * Result of a deadlock detection run (single- or multi-instance).
 * Immutable value object; the service layer turns it into a stored
 * deadlock event and the UI highlights the involved processes.
 */
public class DetectionResult {

    private final boolean deadlockDetected;
    private final List<Long> deadlockedProcessIds;
    private final List<Long> finishedProcessIds;
    private final int[] finalWorkVector;
    private final String explanation;
    private final List<AlgorithmStep> steps;

    public DetectionResult(boolean deadlockDetected,
                           List<Long> deadlockedProcessIds,
                           List<Long> finishedProcessIds,
                           int[] finalWorkVector,
                           String explanation,
                           List<AlgorithmStep> steps) {
        this.deadlockDetected = deadlockDetected;
        this.deadlockedProcessIds = deadlockedProcessIds;
        this.finishedProcessIds = finishedProcessIds;
        this.finalWorkVector = finalWorkVector;
        this.explanation = explanation;
        this.steps = steps;
    }

    public boolean isDeadlockDetected() {
        return deadlockDetected;
    }

    public List<Long> getDeadlockedProcessIds() {
        return deadlockedProcessIds;
    }

    public List<Long> getFinishedProcessIds() {
        return finishedProcessIds;
    }

    public int[] getFinalWorkVector() {
        return finalWorkVector;
    }

    public String getExplanation() {
        return explanation;
    }

    public List<AlgorithmStep> getSteps() {
        return steps;
    }
}
