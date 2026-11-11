package com.paradox.algorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * Deadlock detection for multiple resource instances - the standard
 * Work/Finish algorithm (Silberschatz et al.).
 *
 * A process can finish when its pending request fits into the current work
 * vector; finishing returns its allocations to Work. Whatever remains
 * unfinished is deadlocked. This correctly distinguishes "the RAG has a
 * cycle" from "the system is deadlocked" when instances are shared.
 */
public final class MultiInstanceDetector {

    private MultiInstanceDetector() {
    }

    public static DetectionResult detect(SystemState state) {
        int n = state.processCount();
        int[] work = state.available().clone();
        boolean[] finish = new boolean[n];
        List<AlgorithmStep> steps = new ArrayList<>();
        List<Long> finishedOrder = new ArrayList<>();

        steps.add(new AlgorithmStep(1, "Initialize",
                "Work = Available " + vectorToString(work)
                        + "; Finish[i] = false for all processes"));

        // A process holding nothing can always "finish".
        for (int i = 0; i < n; i++) {
            if (!finish[i] && hasNoAllocation(state, i) && hasNoRequest(state, i)) {
                finish[i] = true;
                finishedOrder.add(state.processIds().get(i));
                steps.add(new AlgorithmStep(steps.size() + 1, "Trivial finish",
                        "P" + state.processIds().get(i)
                                + " holds and requests nothing -> Finish[P"
                                + state.processIds().get(i) + "] = true"));
            }
        }

        boolean progress = true;
        while (progress) {
            progress = false;
            for (int i = 0; i < n; i++) {
                if (finish[i] || !fits(state.request()[i], work)) {
                    continue;
                }
                addVector(work, state.allocation()[i]);
                finish[i] = true;
                finishedOrder.add(state.processIds().get(i));
                steps.add(new AlgorithmStep(steps.size() + 1, "Process can finish",
                        "Request[P" + state.processIds().get(i) + "] "
                                + vectorToString(state.request()[i])
                                + " <= Work -> P" + state.processIds().get(i)
                                + " finishes; Work += Allocation -> Work = "
                                + vectorToString(work)));
                progress = true;
            }
        }

        List<Long> deadlockedIds = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!finish[i]) {
                deadlockedIds.add(state.processIds().get(i));
            }
        }

        String explanation;
        if (deadlockedIds.isEmpty()) {
            steps.add(new AlgorithmStep(steps.size() + 1, "Verdict",
                    "All processes can finish: no deadlock"));
            explanation = "Every process can complete with the currently available "
                    + "instances, so no deadlock exists.";
        } else {
            steps.add(new AlgorithmStep(steps.size() + 1, "Verdict",
                    "Deadlock detected involving " + deadlockedIds.size() + " process(es)"));
            explanation = "The pending requests of " + nameList(deadlockedIds)
                    + " can never be satisfied by the remaining instances; "
                    + "these processes are deadlocked.";
        }

        return new DetectionResult(!deadlockedIds.isEmpty(), deadlockedIds,
                finishedOrder, work, explanation, steps);
    }

    private static boolean hasNoAllocation(SystemState state, int i) {
        return !anyPositive(state.allocation()[i]);
    }

    private static boolean hasNoRequest(SystemState state, int i) {
        return !anyPositive(state.request()[i]);
    }

    private static boolean anyPositive(int[] vector) {
        for (int value : vector) {
            if (value > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean fits(int[] request, int[] work) {
        for (int r = 0; r < work.length; r++) {
            if (request[r] > work[r]) {
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

    static String vectorToString(int[] vector) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(')').toString();
    }

    static String nameList(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(i == ids.size() - 1 ? " and " : ", ");
            }
            sb.append('P').append(ids.get(i));
        }
        return sb.toString();
    }
}
