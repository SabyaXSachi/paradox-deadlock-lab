package com.paradox.algorithm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deadlock detection for the single-instance case via the Resource
 * Allocation Graph.
 *
 * A wait-for graph is derived from the state: Pi waits for Pj when Pi has a
 * pending request for a resource that Pj holds. With one instance per
 * resource type, a cycle in this graph means deadlock, and every process
 * that can reach a cycle through a wait edge is blocked forever, too.
 */
public final class SingleInstanceDetector {

    private SingleInstanceDetector() {
    }

    public static DetectionResult detect(SystemState state) {
        int n = state.processCount();
        List<AlgorithmStep> steps = new ArrayList<>();
        steps.add(new AlgorithmStep(1, "Build wait-for graph",
                "Pi waits for Pj when Pi requests a resource held by Pj"));

        // waitFor[i] = set of j that Pi waits for
        List<Set<Integer>> waitFor = new ArrayList<>();
        List<String> edgeDescriptions = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            waitFor.add(new LinkedHashSet<>());
        }
        for (int i = 0; i < n; i++) {
            for (int r = 0; r < state.resourceCount(); r++) {
                if (state.request()[i][r] <= 0) {
                    continue;
                }
                for (int j = 0; j < n; j++) {
                    if (j != i && state.allocation()[j][r] > 0) {
                        waitFor.get(i).add(j);
                        edgeDescriptions.add("P" + state.processIds().get(i)
                                + " waits for P" + state.processIds().get(j));
                    }
                }
            }
        }
        int stepNo = 2;
        for (String edge : edgeDescriptions) {
            steps.add(new AlgorithmStep(stepNo++, "Wait edge", edge));
        }

        // Depth-first search for cycles (white/gray/black coloring).
        int[] color = new int[n];           // 0 white, 1 gray, 2 black
        int[] parent = new int[n];
        Set<Integer> deadlocked = new LinkedHashSet<>();
        List<String> cycleDescriptions = new ArrayList<>();

        for (int start = 0; start < n; start++) {
            if (color[start] != 0) {
                continue;
            }
            dfs(start, waitFor, color, parent, deadlocked, cycleDescriptions,
                    state.processIds());
        }

        // Anyone who waits (directly or transitively) on a deadlocked
        // process can never run either.
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int i = 0; i < n; i++) {
                if (deadlocked.contains(i)) {
                    continue;
                }
                for (int j : waitFor.get(i)) {
                    if (deadlocked.contains(j)) {
                        deadlocked.add(i);
                        grew = true;
                        break;
                    }
                }
            }
        }

        String explanation;
        if (cycleDescriptions.isEmpty()) {
            steps.add(new AlgorithmStep(stepNo, "Verdict",
                    "No cycle in the wait-for graph: no deadlock"));
            explanation = "No cycle exists in the wait-for graph, so no deadlock exists.";
        } else {
            steps.add(new AlgorithmStep(stepNo, "Verdict",
                    "Cycle(s) found: deadlock detected"));
            explanation = "Circular wait detected: "
                    + String.join("; ", cycleDescriptions)
                    + ". With one instance per resource type every cycle is a deadlock.";
        }

        List<Long> deadlockedIds = deadlocked.stream()
                .map(i -> state.processIds().get(i)).toList();
        List<Long> finishedIds = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!deadlocked.contains(i)) {
                finishedIds.add(state.processIds().get(i));
            }
        }
        return new DetectionResult(!cycleDescriptions.isEmpty(), deadlockedIds,
                finishedIds, state.available().clone(), explanation, steps);
    }

    private static void dfs(int node, List<Set<Integer>> waitFor, int[] color,
                            int[] parent, Set<Integer> deadlocked,
                            List<String> cycleDescriptions, List<Long> processIds) {
        color[node] = 1; // gray: on the current path
        for (int next : waitFor.get(node)) {
            if (color[next] == 1) {
                // Back edge found: reconstruct the cycle from parent links.
                StringBuilder cycle = new StringBuilder();
                int current = node;
                List<Integer> chain = new ArrayList<>();
                while (current != next) {
                    chain.add(current);
                    current = parent[current];
                }
                chain.add(next);
                java.util.Collections.reverse(chain);
                for (int member : chain) {
                    cycle.append('P').append(processIds.get(member)).append(" → ");
                }
                cycle.append('P').append(processIds.get(next));
                cycleDescriptions.add(cycle.toString());
                deadlocked.addAll(chain);
            } else if (color[next] == 0) {
                parent[next] = node;
                dfs(next, waitFor, color, parent, deadlocked, cycleDescriptions, processIds);
            }
        }
        color[node] = 2; // black: fully explored
    }
}
