package com.paradox.algorithm;

import java.util.List;

/**
 * Result of the Banker's safety algorithm or of a resource-request
 * evaluation. Immutable value object.
 */
public class BankersResult {

    /** Outcome of a request evaluation. */
    public enum Decision {
        /** State stays safe after granting: the request may be granted. */
        GRANT,
        /** Granting would lead to an unsafe state: the process must wait. */
        WAIT_UNSAFE,
        /** Request exceeds the process maximum claim: protocol error. */
        DENY_EXCEEDS_MAXIMUM,
        /** Request exceeds currently available instances: process waits. */
        WAIT_NO_INSTANCES
    }

    private final boolean safe;
    private final List<Long> safeSequence;
    private final int[] finalWorkVector;
    private final String message;
    private final List<AlgorithmStep> steps;
    private final Decision decision;

    private BankersResult(boolean safe, List<Long> safeSequence, int[] finalWorkVector,
                          String message, List<AlgorithmStep> steps, Decision decision) {
        this.safe = safe;
        this.safeSequence = safeSequence;
        this.finalWorkVector = finalWorkVector;
        this.message = message;
        this.steps = steps;
        this.decision = decision;
    }

    /** Result of a pure safety check. */
    public static BankersResult safetyResult(boolean safe, List<Long> safeSequence,
                                             int[] finalWorkVector, String message,
                                             List<AlgorithmStep> steps) {
        return new BankersResult(safe, safeSequence, finalWorkVector, message, steps, null);
    }

    /** Result of a resource-request evaluation. */
    public static BankersResult requestResult(Decision decision, String message,
                                              List<AlgorithmStep> steps,
                                              List<Long> hypotheticalSafeSequence,
                                              int[] hypotheticalWork) {
        boolean grantable = decision == Decision.GRANT;
        return new BankersResult(grantable, hypotheticalSafeSequence, hypotheticalWork,
                message, steps, decision);
    }

    public boolean isSafe() {
        return safe;
    }

    public List<Long> getSafeSequence() {
        return safeSequence;
    }

    public int[] getFinalWorkVector() {
        return finalWorkVector;
    }

    public String getMessage() {
        return message;
    }

    public List<AlgorithmStep> getSteps() {
        return steps;
    }

    public Decision getDecision() {
        return decision;
    }
}
