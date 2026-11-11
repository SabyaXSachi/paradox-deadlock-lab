package com.paradox.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-instance detection (Work/Finish algorithm). The key property: a
 * cycle in the resource allocation graph does NOT imply deadlock when
 * resource types have several instances.
 */
class MultiInstanceDetectorTest {

    private static SystemState state(int[] available, int[][] allocation, int[][] request) {
        List<Long> ids = List.of(1L, 2L, 3L, 4L).subList(0, allocation.length);
        return new SystemState(ids, available, allocation, request);
    }

    @Test
    void allocationGraphCycleWithoutDeadlockIsResolvedCorrectly() {
        // R1 has 2 instances, R2 has 1. The RAG contains the cycle
        // P2 -> R1 -> P2 (P2 requests one more R1 while holding one).
        // Still, no deadlock: P3 releases R2, then P1 and P2 can proceed.
        SystemState state = state(new int[]{0, 0},
                new int[][]{{1, 0}, {1, 0}, {0, 1}},
                new int[][]{{0, 1}, {1, 0}, {0, 0}});

        DetectionResult result = MultiInstanceDetector.detect(state);

        assertFalse(result.isDeadlockDetected());
        assertEquals(3, result.getFinishedProcessIds().size());
        // Final work equals the total instances of both resources.
        assertEquals(2, result.getFinalWorkVector()[0]);
        assertEquals(1, result.getFinalWorkVector()[1]);
    }

    @Test
    void genuineDeadlockWithMultipleInstancesIsDetected() {
        // R1 has 2 instances; both are held and both processes want one more.
        SystemState state = state(new int[]{0},
                new int[][]{{1}, {1}},
                new int[][]{{1}, {1}});

        DetectionResult result = MultiInstanceDetector.detect(state);

        assertTrue(result.isDeadlockDetected());
        assertEquals(List.of(1L, 2L), result.getDeadlockedProcessIds());
    }

    @Test
    void deadlockIsReportedOnlyForBlockedProcesses() {
        // R1: 3 instances, R2: 2. P3 can finish, P1 and P2 block each other.
        SystemState state = state(new int[]{0, 1},
                new int[][]{{2, 0}, {1, 0}, {0, 1}},
                new int[][]{{1, 2}, {2, 0}, {0, 0}});

        DetectionResult result = MultiInstanceDetector.detect(state);

        assertTrue(result.isDeadlockDetected());
        assertEquals(List.of(1L, 2L), result.getDeadlockedProcessIds());
        assertEquals(List.of(3L), result.getFinishedProcessIds());
    }

    @Test
    void noPendingRequestsMeansNoDeadlock() {
        SystemState state = state(new int[]{1, 0},
                new int[][]{{1, 2}, {0, 0}},
                new int[][]{{0, 0}, {0, 0}});

        DetectionResult result = MultiInstanceDetector.detect(state);

        assertFalse(result.isDeadlockDetected());
        assertTrue(result.getExplanation().contains("no deadlock"));
    }

    @Test
    void stepsTraceWorkVectorGrowth() {
        SystemState state = state(new int[]{0, 0},
                new int[][]{{1, 0}, {1, 0}, {0, 1}},
                new int[][]{{0, 1}, {1, 0}, {0, 0}});

        DetectionResult result = MultiInstanceDetector.detect(state);

        assertTrue(result.getSteps().size() >= 4);
        assertTrue(result.getSteps().get(result.getSteps().size() - 1)
                .action().contains("Verdict"));
    }
}
