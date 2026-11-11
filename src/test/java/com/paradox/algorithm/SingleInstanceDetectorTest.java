package com.paradox.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Single-instance deadlock detection via the wait-for graph.
 * With one instance per resource type, every cycle is a deadlock.
 */
class SingleInstanceDetectorTest {

    private static SystemState state(int[] available, int[][] allocation, int[][] request) {
        List<Long> ids = List.of(1L, 2L, 3L, 4L).subList(0, allocation.length);
        return new SystemState(ids, available, allocation, request);
    }

    @Test
    void noCycleMeansNoDeadlock() {
        // P1 holds R1, P2 holds R2, nobody waits.
        SystemState state = state(new int[]{0, 0},
                new int[][]{{1, 0}, {0, 1}},
                new int[][]{{0, 0}, {0, 0}});

        DetectionResult result = SingleInstanceDetector.detect(state);

        assertFalse(result.isDeadlockDetected());
        assertEquals(0, result.getDeadlockedProcessIds().size());
        assertEquals(2, result.getFinishedProcessIds().size());
    }

    @Test
    void twoProcessDeadlockIsDetected() {
        // P1 holds R1 and wants R2; P2 holds R2 and wants R1.
        SystemState state = state(new int[]{0, 0},
                new int[][]{{1, 0}, {0, 1}},
                new int[][]{{0, 1}, {1, 0}});

        DetectionResult result = SingleInstanceDetector.detect(state);

        assertTrue(result.isDeadlockDetected());
        assertEquals(List.of(1L, 2L), result.getDeadlockedProcessIds());
        assertTrue(result.getExplanation().contains("P1"));
        assertTrue(result.getExplanation().contains("P2"));
    }

    @Test
    void threeProcessCircularWaitIsDetected() {
        // P1 holds R1 wants R2; P2 holds R2 wants R3; P3 holds R3 wants R1.
        SystemState state = state(new int[]{0, 0, 0},
                new int[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}},
                new int[][]{{0, 1, 0}, {0, 0, 1}, {1, 0, 0}});

        DetectionResult result = SingleInstanceDetector.detect(state);

        assertTrue(result.isDeadlockDetected());
        assertEquals(List.of(1L, 2L, 3L), result.getDeadlockedProcessIds());
        // The explanation reconstructs the circular wait chain.
        assertTrue(result.getExplanation().contains("→"));
    }

    @Test
    void processWaitingOnDeadlockedCycleIsBlockedToo() {
        // P1-P2 form a cycle over R1/R2; P3 holds R3 and waits for R1.
        // P3 is not on the cycle but can never run either.
        SystemState state = state(new int[]{0, 0, 0},
                new int[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}},
                new int[][]{{0, 1, 0}, {1, 0, 0}, {1, 0, 0}});

        DetectionResult result = SingleInstanceDetector.detect(state);

        assertTrue(result.isDeadlockDetected());
        assertEquals(List.of(1L, 2L, 3L), result.getDeadlockedProcessIds());
    }

    @Test
    void everyRunProducesAStepTrace() {
        SystemState state = state(new int[]{0, 0},
                new int[][]{{1, 0}, {0, 1}},
                new int[][]{{0, 1}, {1, 0}});

        DetectionResult result = SingleInstanceDetector.detect(state);

        assertTrue(result.getSteps().size() >= 3);
        assertTrue(result.getSteps().get(0).action().contains("wait-for"));
    }
}
