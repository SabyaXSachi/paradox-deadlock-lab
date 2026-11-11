package com.paradox.model;

import java.util.List;

/** Outcome of one recovery operation. */
public class RecoveryResult {

    private final RecoveryStrategy strategy;
    private final String targetDescription;
    private final boolean success;
    private final List<Long> deadlockedBefore;
    private final List<Long> deadlockedAfter;
    private final String summary;

    public RecoveryResult(RecoveryStrategy strategy, String targetDescription,
                          boolean success, List<Long> deadlockedBefore,
                          List<Long> deadlockedAfter, String summary) {
        this.strategy = strategy;
        this.targetDescription = targetDescription;
        this.success = success;
        this.deadlockedBefore = deadlockedBefore;
        this.deadlockedAfter = deadlockedAfter;
        this.summary = summary;
    }

    public RecoveryStrategy getStrategy() {
        return strategy;
    }

    public String getTargetDescription() {
        return targetDescription;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<Long> getDeadlockedBefore() {
        return deadlockedBefore;
    }

    public List<Long> getDeadlockedAfter() {
        return deadlockedAfter;
    }

    public String getSummary() {
        return summary;
    }
}
