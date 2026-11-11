package com.paradox.algorithm;

import java.util.List;

/**
 * Immutable snapshot of the simulation state in matrix form - the input of
 * every detection and avoidance algorithm. All vectors/matrices are ordered
 * exactly like {@code processIds} and the resource ordering chosen by the
 * caller, so the algorithms stay independent of the engine and the database.
 *
 * @param processIds  ids of the rows of both matrices
 * @param available   free instances per resource type
 * @param allocation  allocation[process][resource] = held instances
 * @param request     request[process][resource] = pending instances
 */
public record SystemState(List<Long> processIds, int[] available,
                          int[][] allocation, int[][] request) {

    public int processCount() {
        return processIds.size();
    }

    public int resourceCount() {
        return available.length;
    }
}
