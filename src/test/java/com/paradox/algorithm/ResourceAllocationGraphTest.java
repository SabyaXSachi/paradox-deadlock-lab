package com.paradox.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Resource Allocation Graph model used by the visualizer.
 */
class ResourceAllocationGraphTest {

    @Test
    void detectsTwoProcessCycle() {
        ResourceAllocationGraph graph = new ResourceAllocationGraph();
        graph.getProcessNodes().addAll(List.of("P1", "P2"));
        graph.getResourceNodes().addAll(List.of("R1", "R2"));
        // P1 holds R1 and requests R2; P2 holds R2 and requests R1.
        graph.getAssignmentEdges().add(new String[]{"R1", "P1"});
        graph.getAssignmentEdges().add(new String[]{"R2", "P2"});
        graph.getRequestEdges().add(new String[]{"P1", "R2"});
        graph.getRequestEdges().add(new String[]{"P2", "R1"});

        List<List<String>> cycles = graph.findCycles();

        assertFalse(cycles.isEmpty());
        assertTrue(graph.hasCycle());
    }

    @Test
    void acyclicGraphHasNoCycle() {
        ResourceAllocationGraph graph = new ResourceAllocationGraph();
        graph.getProcessNodes().add("P1");
        graph.getResourceNodes().add("R1");
        graph.getAssignmentEdges().add(new String[]{"R1", "P1"});

        assertFalse(graph.hasCycle());
    }

    @Test
    void threeProcessCircularWaitFormsOneCycle() {
        ResourceAllocationGraph graph = new ResourceAllocationGraph();
        graph.getProcessNodes().addAll(List.of("P1", "P2", "P3"));
        graph.getResourceNodes().addAll(List.of("R1", "R2", "R3"));
        graph.getAssignmentEdges().add(new String[]{"R1", "P1"});
        graph.getAssignmentEdges().add(new String[]{"R2", "P2"});
        graph.getAssignmentEdges().add(new String[]{"R3", "P3"});
        graph.getRequestEdges().add(new String[]{"P1", "R2"});
        graph.getRequestEdges().add(new String[]{"P2", "R3"});
        graph.getRequestEdges().add(new String[]{"P3", "R1"});

        List<List<String>> cycles = graph.findCycles();

        assertEquals(1, cycles.size());
        List<String> cycle = cycles.get(0);
        assertEquals(cycle.get(0), cycle.get(cycle.size() - 1), "cycle must be closed");
        assertEquals(7, cycle.size()); // P1 R2 P2 R3 P3 R1 P1
    }

    @Test
    void cycleExistsButMultiInstanceDetectionMayStillSayNoDeadlock() {
        // Documented integration case: R1 has two instances, P2 holds one and
        // requests another (self-cycle P2 -> R1 -> P2 in the RAG), while P3
        // holds R2 and wants nothing. The RAG has a cycle, yet the
        // multi-instance detector proves no deadlock.
        ResourceAllocationGraph graph = new ResourceAllocationGraph();
        graph.getProcessNodes().addAll(List.of("P1", "P2", "P3"));
        graph.getResourceNodes().addAll(List.of("R1", "R2"));
        graph.getAssignmentEdges().add(new String[]{"R1", "P1"});
        graph.getAssignmentEdges().add(new String[]{"R1", "P2"});
        graph.getAssignmentEdges().add(new String[]{"R2", "P2"});
        graph.getAssignmentEdges().add(new String[]{"R2", "P3"});
        graph.getRequestEdges().add(new String[]{"P1", "R2"});
        graph.getRequestEdges().add(new String[]{"P2", "R1"});

        assertTrue(graph.hasCycle(), "RAG should contain the P2-R1 cycle");

        SystemState state = new SystemState(List.of(1L, 2L, 3L),
                new int[]{0, 0},
                new int[][]{{1, 0}, {1, 0}, {0, 1}},
                new int[][]{{0, 1}, {1, 0}, {0, 0}});
        DetectionResult result = MultiInstanceDetector.detect(state);
        assertFalse(result.isDeadlockDetected(),
                "multi-instance detection must not report a deadlock here");
    }
}
