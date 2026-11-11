package com.paradox.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Banker's Algorithm tests, based on the classic Silberschatz example:
 * 5 processes, resources A=10, B=5, C=7, Available = (3,3,2).
 */
class BankersAlgorithmTest {

    private static final int[][] ALLOCATION = {
            {0, 1, 0},   // P1 (P0 in the book)
            {2, 0, 0},   // P2
            {3, 0, 2},   // P3
            {2, 1, 1},   // P4
            {0, 0, 2}};  // P5

    private static final int[][] MAX = {
            {7, 5, 3},
            {3, 2, 2},
            {9, 0, 2},
            {2, 2, 2},
            {4, 3, 3}};

    private static SystemState classicState() {
        return new SystemState(List.of(1L, 2L, 3L, 4L, 5L),
                new int[]{3, 3, 2}, clone(ALLOCATION), new int[5][3]);
    }

    @Test
    void classicSafeStateProducesAValidSafeSequence() {
        BankersResult result = BankersAlgorithm.runSafetyAlgorithm(classicState(), MAX);

        assertTrue(result.isSafe());
        assertEquals(5, result.getSafeSequence().size());
        // All allocated instances return to work at the end.
        assertEquals(10, result.getFinalWorkVector()[0]);
        assertEquals(5, result.getFinalWorkVector()[1]);
        assertEquals(7, result.getFinalWorkVector()[2]);
        assertTrue(isValidSafeSequence(classicState(), result.getSafeSequence()));
    }

    @Test
    void needMatrixIsMaxMinusAllocation() {
        int[][] need = BankersAlgorithm.need(classicState(), MAX);
        assertEquals(7, need[0][0]);   // P1: 7-0
        assertEquals(4, need[0][1]);   // P1: 5-1
        assertEquals(1, need[1][0]);   // P2: 3-2
        assertEquals(0, need[2][2]);   // P3: 2-2
    }

    @Test
    void needBelowAllocationIsRejected() {
        int[][] brokenMax = clone(MAX);
        brokenMax[1][0] = 1;           // P2 holds 2 x A but claims max 1
        assertThrows(IllegalArgumentException.class,
                () -> BankersAlgorithm.need(classicState(), brokenMax));
    }

    @Test
    void safeRequestIsAdvisedToBeGranted() {
        // Textbook: P1 (index 1) requests (1,0,2); granting keeps the state safe.
        SystemState state = classicState();

        BankersResult result = BankersAlgorithm.evaluateRequest(state, MAX, 1,
                new int[]{1, 0, 2});

        assertEquals(BankersResult.Decision.GRANT, result.getDecision());
        assertTrue(result.isSafe());
    }

    @Test
    void unsafeRequestIsAdvisedToWait() {
        // Textbook continuation: after P1 received (1,0,2), Available = (2,3,0).
        // P1 (index 0) then requests (0,2,0): granting would be UNSAFE.
        int[][] allocation = clone(ALLOCATION);
        allocation[1][0] += 1;         // P2 now holds 3 x A... (index 1 got the grant)
        allocation[1][2] += 2;
        SystemState state = new SystemState(List.of(1L, 2L, 3L, 4L, 5L),
                new int[]{2, 3, 0}, allocation, new int[5][3]);

        BankersResult result = BankersAlgorithm.evaluateRequest(state, MAX, 0,
                new int[]{0, 2, 0});

        assertEquals(BankersResult.Decision.WAIT_UNSAFE, result.getDecision());
        assertFalse(result.isSafe());
        assertTrue(result.getMessage().contains("unsafe"));
    }

    @Test
    void requestAboveMaximumClaimIsRejected() {
        // Need of P1 (index 0) is (7,4,3): asking for 8 x A exceeds the claim.
        BankersResult result = BankersAlgorithm.evaluateRequest(classicState(), MAX, 0,
                new int[]{8, 0, 0});

        assertEquals(BankersResult.Decision.DENY_EXCEEDS_MAXIMUM, result.getDecision());
    }

    @Test
    void requestAboveAvailableMakesProcessWait() {
        // Available is (3,3,2): 4 x B cannot be granted right now.
        BankersResult result = BankersAlgorithm.evaluateRequest(classicState(), MAX, 0,
                new int[]{0, 4, 0});

        assertEquals(BankersResult.Decision.WAIT_NO_INSTANCES, result.getDecision());
    }

    @Test
    void unsafeStateIsNotReportedAsDeadlock() {
        // An unsafe state is a risk; the message must not claim a deadlock exists.
        int[][] allocation = clone(ALLOCATION);
        allocation[1][0] += 1;
        allocation[1][2] += 2;
        SystemState state = new SystemState(List.of(1L, 2L, 3L, 4L, 5L),
                new int[]{2, 3, 0}, allocation, new int[5][3]);

        BankersResult result = BankersAlgorithm.evaluateRequest(state, MAX, 0,
                new int[]{0, 2, 0});

        assertFalse(result.getMessage().toLowerCase().contains("deadlock exists"));
    }

    @Test
    void everySafetyRunProducesAStepTrace() {
        BankersResult result = BankersAlgorithm.runSafetyAlgorithm(classicState(), MAX);
        assertTrue(result.getSteps().size() >= 7);
    }

    // ---------- helpers ----------

    /** Simulates the claimed sequence; every process must be able to finish. */
    private static boolean isValidSafeSequence(SystemState state, List<Long> sequence) {
        int[][] need = BankersAlgorithm.need(state, MAX);
        int[] work = state.available().clone();
        boolean[] done = new boolean[state.processCount()];
        for (long id : sequence) {
            int index = state.processIds().indexOf(id);
            if (index < 0 || done[index] || !fits(need[index], work)) {
                return false;
            }
            done[index] = true;
            for (int r = 0; r < work.length; r++) {
                work[r] += state.allocation()[index][r];
            }
        }
        for (boolean finished : done) {
            if (!finished) {
                return false;
            }
        }
        return true;
    }

    private static boolean fits(int[] need, int[] work) {
        for (int r = 0; r < work.length; r++) {
            if (need[r] > work[r]) {
                return false;
            }
        }
        return true;
    }

    private static int[][] clone(int[][] matrix) {
        int[][] copy = new int[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            copy[i] = matrix[i].clone();
        }
        return copy;
    }
}
