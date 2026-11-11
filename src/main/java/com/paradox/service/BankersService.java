package com.paradox.service;

import com.paradox.algorithm.BankersAlgorithm;
import com.paradox.algorithm.BankersResult;
import com.paradox.algorithm.SystemState;
import com.paradox.database.Db;
import com.paradox.model.EventType;
import com.paradox.model.SimProcess;
import com.paradox.util.JsonUtil;
import com.paradox.util.ValidationException;

import java.util.List;

/**
 * Banker's Algorithm service: maximum-claim management, safety checks and
 * request evaluation. Banker's Algorithm is deadlock AVOIDANCE - the service
 * never changes the simulation status to DEADLOCKED; it only reports safe /
 * unsafe. Requests evaluated here are advisory; actually applying a request
 * goes through the AllocationService.
 */
public class BankersService {

    private final SimulationEngine engine;
    private final SimulationService simulationService;

    public BankersService(SimulationEngine engine, SimulationService simulationService) {
        this.engine = engine;
        this.simulationService = simulationService;
    }

    /** Stores the maximum claim of one (process, resource) pair. */
    public void setMaxClaim(long processId, long resourceId, int maxInstances) {
        requireSimulation();
        if (maxInstances < 0) {
            throw new ValidationException("Maximum claim cannot be negative");
        }
        int currentAllocation = engine.allocationOf(processId, resourceId);
        if (maxInstances < currentAllocation) {
            throw new ValidationException("Maximum claim (" + maxInstances
                    + ") cannot be below the current allocation (" + currentAllocation + ")");
        }
        Db.inTransaction(conn -> {
            simulationService.maxClaimRepository().setMax(conn, processId, resourceId,
                    maxInstances);
            return null;
        });
        engine.setMaxClaim(processId, resourceId, maxInstances);
        engine.bumpStep();
        engine.fireChange(simulationService.newEvent(EventType.BANKERS_CHECK,
                "Maximum claim set: " + processName(processId) + " -> "
                        + resourceName(resourceId) + " = " + maxInstances));
    }

    /**
     * Runs the safety algorithm on the current state.
     *
     * @throws ValidationException when the maximum-claim matrix is incomplete
     */
    public BankersResult runSafetyCheck() {
        requireSimulation();
        SystemState state = buildCompleteState();
        int[][] max = maxMatrix();

        BankersResult result = BankersAlgorithm.runSafetyAlgorithm(state, max);
        persistCheck("SAFETY", result.isSafe()
                        ? "State is SAFE (sequence " + safeSequenceText(result) + ")"
                        : "State is UNSAFE - no safe sequence exists",
                result.isSafe());
        return result;
    }

    /**
     * Evaluates a hypothetical request with the Banker's request algorithm.
     * No simulation state is changed.
     */
    public BankersResult evaluateRequest(long processId, long resourceId, int instances) {
        requireSimulation();
        if (instances <= 0) {
            throw new ValidationException("Instance count must be at least 1");
        }
        SystemState state = buildCompleteState();
        int[][] max = maxMatrix();

        List<Long> processIds = state.processIds();
        int processIndex = processIds.indexOf(processId);
        if (processIndex < 0) {
            throw new ValidationException("Unknown or terminated process");
        }
        int resourceIndex = -1;
        for (int r = 0; r < engine.getResources().size(); r++) {
            if (engine.getResources().get(r).getId() == resourceId) {
                resourceIndex = r;
            }
        }
        if (resourceIndex < 0) {
            throw new ValidationException("Unknown resource");
        }

        int[] requestVector = new int[state.resourceCount()];
        requestVector[resourceIndex] = instances;

        BankersResult result = BankersAlgorithm.evaluateRequest(state, max,
                processIndex, requestVector);
        persistCheck("REQUEST", "P" + processId + " requests " + instances
                + " x " + resourceName(resourceId) + " -> " + result.getDecision(),
                result.getDecision() == com.paradox.algorithm.BankersResult.Decision.GRANT);
        return result;
    }

    // ---------- helpers ----------

    /** State including only active processes; validation that Max is complete. */
    private SystemState buildCompleteState() {
        DetectionService detection = new DetectionService(engine, simulationService);
        SystemState state = detection.buildState();
        for (long processId : state.processIds()) {
            for (var resource : engine.getResources()) {
                if (!engine.getMaxClaims()
                        .getOrDefault(processId, java.util.Map.of())
                        .containsKey(resource.getId())) {
                    throw new ValidationException("Banker's Algorithm needs the maximum "
                            + "claim of every process for every resource. Missing: "
                            + processName(processId) + " for " + resource.getName()
                            + ". Set it in the table above.");
                }
            }
        }
        return state;
    }

    private int[][] maxMatrix() {
        DetectionService detection = new DetectionService(engine, simulationService);
        SystemState state = detection.buildState();
        int[][] max = new int[state.processCount()][state.resourceCount()];
        for (int i = 0; i < state.processCount(); i++) {
            for (int r = 0; r < state.resourceCount(); r++) {
                long resourceId = engine.getResources().get(r).getId();
                max[i][r] = engine.maxClaimOf(state.processIds().get(i), resourceId);
            }
        }
        return max;
    }

    private void persistCheck(String kind, String message, boolean positive) {
        Db.inTransaction(conn ->
                simulationService.eventRepository().insert(conn,
                        simulationService.newEvent(EventType.BANKERS_CHECK,
                                kind + ": " + message,
                                "{\"kind\":\"" + kind + "\",\"positive\":" + positive + "}")));
        engine.bumpStep();
        engine.fireChange(simulationService.newEvent(EventType.BANKERS_CHECK, message));
    }

    private String safeSequenceText(BankersResult result) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.getSafeSequence().size(); i++) {
            if (i > 0) {
                sb.append(" -> ");
            }
            sb.append('P').append(result.getSafeSequence().get(i));
        }
        return sb.toString();
    }

    private String processName(long processId) {
        return engine.findProcess(processId).map(SimProcess::getName)
                .orElse("P" + processId);
    }

    private String resourceName(long resourceId) {
        return engine.findResource(resourceId)
                .map(r -> r.getName()).orElse("R" + resourceId);
    }

    private void requireSimulation() {
        if (!engine.hasSimulation()) {
            throw new ValidationException("Create or open a simulation first");
        }
    }
}
