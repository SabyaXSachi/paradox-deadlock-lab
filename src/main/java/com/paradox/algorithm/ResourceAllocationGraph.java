package com.paradox.algorithm;

import com.paradox.model.Allocation;
import com.paradox.model.ProcessState;
import com.paradox.model.Resource;
import com.paradox.model.SimProcess;
import com.paradox.service.SimulationEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Resource Allocation Graph as used by the visualizer.
 *
 * Node keys are prefixed with their type ("P12", "R3"), so process and
 * resource ids never collide. Assignment edges point resource -> process,
 * request edges point process -> resource. {@link #findCycles()} detects
 * cycles for highlighting; for multi-instance resources a cycle alone does
 * not prove deadlock - {@link MultiInstanceDetector} answers that question.
 */
public class ResourceAllocationGraph {

    private final List<String> processNodes = new ArrayList<>();
    private final List<String> resourceNodes = new ArrayList<>();
    private final List<String[]> assignmentEdges = new ArrayList<>(); // R -> P
    private final List<String[]> requestEdges = new ArrayList<>();    // P -> R

    /** Builds the graph from the live engine state. */
    public static ResourceAllocationGraph fromEngine(SimulationEngine engine) {
        ResourceAllocationGraph graph = new ResourceAllocationGraph();
        for (SimProcess process : engine.getProcesses()) {
            if (process.getState() != ProcessState.TERMINATED) {
                graph.processNodes.add(key(process.getId()));
            }
        }
        for (Resource resource : engine.getResources()) {
            graph.resourceNodes.add(resourceKey(resource.getId()));
        }
        for (Allocation allocation : engine.getAllocations()) {
            // Only draw assignments for processes that are still alive.
            boolean alive = engine.findProcess(allocation.getProcessId())
                    .map(p -> p.getState() != ProcessState.TERMINATED)
                    .orElse(false);
            if (alive) {
                graph.assignmentEdges.add(new String[]{
                        resourceKey(allocation.getResourceId()), key(allocation.getProcessId())});
            }
        }
        for (var request : engine.getPendingRequests()) {
            graph.requestEdges.add(new String[]{
                    key(request.getProcessId()), resourceKey(request.getResourceId())});
        }
        return graph;
    }

    public static String key(long processId) {
        return "P" + processId;
    }

    public static String resourceKey(long resourceId) {
        return "R" + resourceId;
    }

    public List<String> getProcessNodes() {
        return processNodes;
    }

    public List<String> getResourceNodes() {
        return resourceNodes;
    }

    public List<String[]> getAssignmentEdges() {
        return assignmentEdges;
    }

    public List<String[]> getRequestEdges() {
        return requestEdges;
    }

    /** True when at least one cycle exists (necessary, not sufficient). */
    public boolean hasCycle() {
        return !findCycles().isEmpty();
    }

    /**
     * Finds cycles in the bipartite graph via depth-first search.
     *
     * @return each cycle as an alternating chain of node keys, e.g.
     *         [P1, R2, P2, R1, P1]
     */
    public List<List<String>> findCycles() {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (String[] edge : assignmentEdges) {
            adjacency.computeIfAbsent(edge[0], k -> new ArrayList<>()).add(edge[1]);
        }
        for (String[] edge : requestEdges) {
            adjacency.computeIfAbsent(edge[0], k -> new ArrayList<>()).add(edge[1]);
        }

        List<List<String>> cycles = new ArrayList<>();
        Map<String, Integer> color = new HashMap<>();      // 0 white, 1 gray, 2 black
        Map<String, String> parent = new HashMap<>();
        for (String node : allNodes()) {
            if (color.getOrDefault(node, 0) == 0) {
                dfs(node, adjacency, color, parent, cycles);
            }
        }
        return cycles;
    }

    private List<String> allNodes() {
        List<String> all = new ArrayList<>(processNodes);
        all.addAll(resourceNodes);
        return all;
    }

    private static void dfs(String node, Map<String, List<String>> adjacency,
                            Map<String, Integer> color, Map<String, String> parent,
                            List<List<String>> cycles) {
        color.put(node, 1);
        for (String next : adjacency.getOrDefault(node, List.of())) {
            int nextColor = color.getOrDefault(next, 0);
            if (nextColor == 1) {
                cycles.add(reconstructCycle(next, node, parent));
            } else if (nextColor == 0) {
                parent.put(next, node);
                dfs(next, adjacency, color, parent, cycles);
            }
        }
        color.put(node, 2);
    }

    private static List<String> reconstructCycle(String start, String end,
                                                 Map<String, String> parent) {
        List<String> chain = new ArrayList<>();
        String current = end;
        chain.add(current);
        while (!current.equals(start)) {
            current = parent.get(current);
            chain.add(current);
        }
        java.util.Collections.reverse(chain);
        chain.add(start); // close the cycle visually
        return chain;
    }
}
