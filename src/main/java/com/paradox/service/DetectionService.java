package com.paradox.service;

import com.paradox.algorithm.AlgorithmStep;
import com.paradox.algorithm.DetectionResult;
import com.paradox.algorithm.MultiInstanceDetector;
import com.paradox.algorithm.SingleInstanceDetector;
import com.paradox.algorithm.SystemState;
import com.paradox.database.Db;
import com.paradox.model.DeadlockEvent;
import com.paradox.model.DetectionType;
import com.paradox.model.EventType;
import com.paradox.model.ProcessState;
import com.paradox.model.SimulationStatus;
import com.paradox.model.SimProcess;
import com.paradox.repository.DeadlockEventRepository;
import com.paradox.util.JsonUtil;
import com.paradox.util.ValidationException;

import java.util.List;

/**
 * Runs deadlock detection on the live engine state and stores the result.
 *
 * The algorithm is chosen automatically: with one instance per resource type
 * the RAG/wait-for cycle detection is used (a cycle is a deadlock); with
 * multiple instances the Work/Finish algorithm decides.
 */
public class DetectionService {

    private final SimulationEngine engine;
    private final SimulationService simulationService;
    private final DeadlockEventRepository deadlockEventRepository = new DeadlockEventRepository();

    public DetectionService(SimulationEngine engine, SimulationService simulationService) {
        this.engine = engine;
        this.simulationService = simulationService;
    }

    /** Runs detection and persists a deadlock event plus the new status. */
    public DetectionResult detect() {
        requireSimulation();
        boolean singleInstance = allResourcesAreSingleInstance();
        DetectionType type = singleInstance ? DetectionType.RAG : DetectionType.MULTI_INSTANCE;

        SystemState state = buildState();
        DetectionResult result = singleInstance
                ? SingleInstanceDetector.detect(state)
                : MultiInstanceDetector.detect(state);

        SimulationStatus newStatus = result.isDeadlockDetected()
                ? SimulationStatus.DEADLOCKED
                : SimulationStatus.RUNNING;

        DeadlockEvent event = new DeadlockEvent();
        event.setSimulationId(engine.getSimulation().getId());
        event.setDetectionType(type);
        event.setDeadlockDetected(result.isDeadlockDetected());
        event.setDeadlockedProcessesJson(JsonUtil.stringArray(
                result.getDeadlockedProcessIds().stream().map(String::valueOf).toList()));
        event.setExplanation(result.getExplanation());
        event.setStepTraceJson(JsonUtil.stringArray(result.getSteps().stream()
                .map(AlgorithmStep::toString).toList()));

        Db.inTransaction(conn -> {
            deadlockEventRepository.insert(conn, event);
            simulationService.simulationRepository().updateStatus(conn,
                    engine.getSimulation().getId(), newStatus);
            simulationService.eventRepository().insert(conn,
                    simulationService.newEvent(EventType.DETECTION,
                            result.isDeadlockDetected()
                                    ? "DEADLOCK detected involving "
                                      + result.getDeadlockedProcessIds().size() + " process(es)"
                                    : "No deadlock detected",
                            "{\"type\":\"" + type + "\",\"deadlock\":"
                                    + result.isDeadlockDetected() + "}"));
            return null;
        });

        engine.getSimulation().setStatus(newStatus);
        engine.bumpStep();
        com.paradox.AppContext.setLastDetectionResult(result);
        engine.fireChange(simulationService.newEvent(EventType.DETECTION,
                result.isDeadlockDetected() ? "Deadlock detected" : "No deadlock detected"));
        return result;
    }

    /** Builds the algorithm input from the live state (terminated excluded). */
    public SystemState buildState() {
        List<SimProcess> active = engine.getProcesses().stream()
                .filter(p -> p.getState() != ProcessState.TERMINATED)
                .toList();
        List<Long> processIds = active.stream().map(SimProcess::getId).toList();
        int resourceCount = engine.getResources().size();

        int[] available = engine.availableVector();
        int[][] allocation = new int[processIds.size()][resourceCount];
        int[][] request = new int[processIds.size()][resourceCount];
        for (int i = 0; i < processIds.size(); i++) {
            for (int r = 0; r < resourceCount; r++) {
                long resourceId = engine.getResources().get(r).getId();
                allocation[i][r] = engine.allocationOf(processIds.get(i), resourceId);
                request[i][r] = engine.pendingRequestOf(processIds.get(i), resourceId);
            }
        }
        return new SystemState(processIds, available, allocation, request);
    }

    private boolean allResourcesAreSingleInstance() {
        return engine.getResources().stream()
                .allMatch(r -> r.getTotalInstances() == 1);
    }

    private void requireSimulation() {
        if (!engine.hasSimulation()) {
            throw new ValidationException("Create or open a simulation first");
        }
    }
}
