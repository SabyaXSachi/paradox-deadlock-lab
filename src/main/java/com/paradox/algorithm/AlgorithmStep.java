package com.paradox.algorithm;

/**
 * One human-readable step of an algorithm execution. The UI renders the
 * step list so users can follow detection and Banker's Algorithm exactly.
 */
public record AlgorithmStep(int stepNumber, String action, String detail) {

    @Override
    public String toString() {
        return stepNumber + ". " + action + (detail.isEmpty() ? "" : " - " + detail);
    }
}
