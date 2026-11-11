package com.paradox.algorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * Banker's Algorithm (Dijkstra): deadlock AVOIDANCE, not detection.
 *
 * The safety algorithm computes whether every process can still complete
 * from the current state and, if so, gives one safe sequence. The request
 * algorithm provisionally grants a request, re-runs the safety algorithm,
 * and advises grant or wait. An unsafe state means "risk", never "deadlock
 * exists" - the UI must keep the two apart.
 */
public final class BankersAlgorithm {

    private BankersAlgorithm() {
    }

    /**
     * Runs the safety algorithm.
     *
     * @param state       available / allocation / request matrices (request is
     *                    ignored here; use {@link #need} to derive Need)
     * @param max         maximum claim matrix, same ordering as state
     */
    public static BankersResult runSafetyAlgorithm(SystemState state, int[][] max) {
        int n = state.processCount();
        int[][] need = need(state, max);
        List<AlgorithmStep> steps = new ArrayList<>();
        steps.add(new AlgorithmStep(1, "Compute Need",
                "Need = Maximum - Allocation (verified to be non-negative)"));

        int[] work = state.available().clone();
        boolean[] finish = new boolean[n];
        List<Long> sequence = new ArrayList<>();

        steps.add(new AlgorithmStep(2, "Initialize",
                "Work = Available " + MultiInstanceDetector.vectorToString(work)
                        + "; Finish[i] = false for all processes"));

        boolean progress = true;
        while (progress) {
            progress = false;
            for (int i = 0; i < n; i++) {
                if (finish[i] || !fits(need[i], work)) {
                    continue;
                }
                String workBefore = MultiInstanceDetector.vectorToString(work);
                addVector(work, state.allocation()[i]);
                finish[i] = true;
                sequence.add(state.processIds().get(i));
                steps.add(new AlgorithmStep(steps.size() + 1, "Process can finish",
                        "Need[P" + state.processIds().get(i) + "] "
                                + MultiInstanceDetector.vectorToString(need[i])
                                + " <= Work " + workBefore
                                + " -> P" + state.processIds().get(i)
                                + " runs to completion; Work += Allocation -> Work = "
                                + MultiInstanceDetector.vectorToString(work)));
                progress = true;
            }
        }

        boolean safe = sequence.size() == n;
        String message;
        if (safe) {
            steps.add(new AlgorithmStep(steps.size() + 1, "Verdict",
                    "SAFE state; safe sequence " + sequenceText(sequence)));
            message = "The state is SAFE. One safe sequence is "
                    + sequenceText(sequence) + ". Every process can complete.";
        } else {
            List<Long> stuck = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!finish[i]) {
                    stuck.add(state.processIds().get(i));
                }
            }
            steps.add(new AlgorithmStep(steps.size() + 1, "Verdict",
                    "UNSAFE state; processes " + MultiInstanceDetector.nameList(stuck)
                            + " cannot be guaranteed completion"));
            message = "The state is UNSAFE. No safe sequence exists; the needs of "
                    + MultiInstanceDetector.nameList(stuck)
                    + " may exceed what will ever be available. An unsafe state is a "
                    + "risk of deadlock, not a detected deadlock.";
        }
        return BankersResult.safetyResult(safe, sequence, work, message, steps);
    }

    /**
     * Banker's resource-request algorithm for one process.
     *
     * @param processIndex row index of the requesting process inside state
     * @param request      requested instances per resource type
     */
    public static BankersResult evaluateRequest(SystemState state, int[][] max,
                                                int processIndex, int[] request) {
        long processId = state.processIds().get(processIndex);
        List<AlgorithmStep> steps = new ArrayList<>();
        int[][] need = need(state, max);

        // Rule 1: Request <= Need, otherwise the claim protocol is violated.
        if (!fits(request, need[processIndex])) {
            steps.add(new AlgorithmStep(1, "Reject",
                    "Request " + MultiInstanceDetector.vectorToString(request)
                            + " exceeds Need[P" + processId + "] "
                            + MultiInstanceDetector.vectorToString(need[processIndex])));
            return BankersResult.requestResult(BankersResult.Decision.DENY_EXCEEDS_MAXIMUM,
                    "Process P" + processId + " requested more than its declared maximum "
                            + "claim allows. The request is rejected.", steps, null, null);
        }

        // Rule 2: Request <= Available, otherwise the process must wait.
        if (!fits(request, state.available())) {
            steps.add(new AlgorithmStep(1, "Wait",
                    "Request " + MultiInstanceDetector.vectorToString(request)
                            + " exceeds Available "
                            + MultiInstanceDetector.vectorToString(state.available())));
            return BankersResult.requestResult(BankersResult.Decision.WAIT_NO_INSTANCES,
                    "Not enough instances are currently available; P" + processId
                            + " must wait.", steps, null, null);
        }

        // Rule 3: provisionally grant, then re-run the safety algorithm.
        int[] available = state.available().clone();
        subtractVector(available, request);
        int[][] allocation = copy(state.allocation());
        for (int r = 0; r < request.length; r++) {
            allocation[processIndex][r] += request[r];
        }
        // In the hypothetical state the pending request is satisfied.
        int[][] newRequest = copy(state.request());
        java.util.Arrays.fill(newRequest[processIndex], 0);
        int[][] newMax = copy(max);

        SystemState hypothetical = new SystemState(state.processIds(), available,
                allocation, newRequest);
        BankersResult safetyAfterGrant = runSafetyAlgorithm(hypothetical, newMax);
        steps.add(new AlgorithmStep(steps.size() + 1, "Provisional grant",
                "Assume the request is granted; Available becomes "
                        + MultiInstanceDetector.vectorToString(available)));
        steps.addAll(safetyAfterGrant.getSteps());

        if (safetyAfterGrant.isSafe()) {
            steps.add(new AlgorithmStep(steps.size() + 1, "Decision",
                    "Grant is safe: state remains SAFE"));
            return BankersResult.requestResult(BankersResult.Decision.GRANT,
                    "Granting the request keeps the state safe (safe sequence "
                            + sequenceText(safetyAfterGrant.getSafeSequence())
                            + "). The request can be granted.", steps,
                    safetyAfterGrant.getSafeSequence(),
                    safetyAfterGrant.getFinalWorkVector());
        }
        steps.add(new AlgorithmStep(steps.size() + 1, "Decision",
                "Grant would create an UNSAFE state: the process must wait"));
        return BankersResult.requestResult(BankersResult.Decision.WAIT_UNSAFE,
                "Granting the request would put the system into an unsafe state; "
                        + "P" + processId + " must wait.", steps, null, null);
    }

    /** Need = Maximum - Allocation. Throws when Max < Allocation (invalid input). */
    public static int[][] need(SystemState state, int[][] max) {
        int[][] need = new int[state.processCount()][state.resourceCount()];
        for (int i = 0; i < need.length; i++) {
            for (int r = 0; r < need[i].length; r++) {
                if (max[i][r] < state.allocation()[i][r]) {
                    throw new IllegalArgumentException(
                            "Maximum claim of P" + state.processIds().get(i)
                                    + " is below its current allocation for resource "
                                    + r);
                }
                need[i][r] = max[i][r] - state.allocation()[i][r];
            }
        }
        return need;
    }

    private static boolean fits(int[] need, int[] work) {
        for (int r = 0; r < work.length; r++) {
            if (need[r] > work[r]) {
                return false;
            }
        }
        return true;
    }

    private static void addVector(int[] target, int[] summand) {
        for (int r = 0; r < target.length; r++) {
            target[r] += summand[r];
        }
    }

    private static void subtractVector(int[] target, int[] subtrahend) {
        for (int r = 0; r < target.length; r++) {
            target[r] -= subtrahend[r];
        }
    }

    private static int[][] copy(int[][] matrix) {
        int[][] result = new int[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            result[i] = matrix[i].clone();
        }
        return result;
    }

    static String sequenceText(List<Long> sequence) {
        StringBuilder sb = new StringBuilder("<");
        for (int i = 0; i < sequence.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('P').append(sequence.get(i));
        }
        return sb.append('>').toString();
    }
}
