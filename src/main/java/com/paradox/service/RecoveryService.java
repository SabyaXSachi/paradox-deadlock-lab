package com.paradox.service;

import com.paradox.database.Db;
import com.paradox.model.EventType;
import com.paradox.model.ProcessState;
import com.paradox.model.RecoveryLog;
import com.paradox.model.RecoveryResult;
import com.paradox.model.RecoveryStrategy;
import com.paradox.model.SimProcess;
import com.paradox.algorithm.DetectionResult;
import com.paradox.repository.RecoveryLogRepository;
import com.paradox.util.ValidationException;

import java.sql.Connection;
import java.util.List;

/**
 * Recovery Engine: process termination and (simulated) resource preemption.
 *
 * Every operation: run detection to capture the "before" state, apply the
 * recovery inside one transaction (including the recovery log), wake waiting
 * processes, then run detection again to verify the outcome.
 *
 * Preemption is explicitly a simulation: the victim process is moved to
 * READY and marked as needing a rollback/restart, mirroring the consistency
 * concern of real preemption.
 */
public class RecoveryService {

    private final SimulationEngine engine;
    private final SimulationService simulationService;
    private final DetectionService detectionService;
    private final ProcessService processService;
    private final RecoveryLogRepository recoveryLogRepository = new RecoveryLogRepository();

    public RecoveryService(SimulationEngine engine, SimulationService simulationService,
                           ProcessService processService) {
        this.engine = engine;
        this.simulationService = simulationService;
        this.processService = processService;
        this.detectionService = new DetectionService(engine, simulationService);
    }

    /** Terminates one selected process and verifies the deadlock is gone. */
    public RecoveryResult terminateOne(long processId) {
        requireSimulation();
        SimProcess target = engine.findProcess(processId)
                .orElseThrow(() -> new ValidationException("Unknown process"));
        if (target.getState() == ProcessState.TERMINATED) {
            throw new ValidationException("Process " + target.getName()
                    + " is already terminated");
        }
        DetectionResult before = detectionService.detect();

        Db.inTransaction(conn -> {
            processService.doTerminate(conn, processId);
            simulationService.eventRepository().insert(conn,
                    simulationService.newEvent(EventType.RECOVERY,
                            "Recovery: terminated process " + target.getName()));
            return null; // detection is re-run below, outside this transaction
        });

        engine.terminateProcess(processId);
        engine.bumpStep();
        engine.fireChange(simulationService.newEvent(EventType.RECOVERY,
                "Process " + target.getName() + " terminated by recovery"));
        new AllocationService(engine, simulationService).tryGrantPending();

        DetectionResult verified = detectionService.detect();
        RecoveryResult result = buildResult(RecoveryStrategy.TERMINATE_ONE,
                "Terminate process " + target.getName(), before, verified);
        persistLog(result.getStrategy(), result.getTargetDescription(), result);
        return result;
    }

    /** Terminates every deadlocked process (the blunt recovery). */
    public RecoveryResult terminateAllDeadlocked() {
        requireSimulation();
        DetectionResult before = detectionService.detect();
        if (!before.isDeadlockDetected()) {
            throw new ValidationException("No deadlock is present - there is nothing "
                    + "to recover from.");
        }
        List<Long> targets = before.getDeadlockedProcessIds();
        Db.inTransaction(conn -> {
            for (long processId : targets) {
                processService.doTerminate(conn, processId);
            }
            simulationService.eventRepository().insert(conn,
                    simulationService.newEvent(EventType.RECOVERY,
                            "Recovery: terminated all deadlocked processes " + targets));
            return null;
        });
        for (long processId : targets) {
            engine.terminateProcess(processId);
        }
        engine.bumpStep();
        engine.fireChange(simulationService.newEvent(EventType.RECOVERY,
                "All deadlocked processes terminated (" + targets.size() + ")"));
        new AllocationService(engine, simulationService).tryGrantPending();

        DetectionResult verified = detectionService.detect();
        RecoveryResult result = buildResult(RecoveryStrategy.TERMINATE_ALL,
                "Terminate all deadlocked processes", before, verified);
        persistLog(result.getStrategy(), result.getTargetDescription(), result);
        return result;
    }

    /**
     * Simulated resource preemption: revokes instances from a process,
     * returns them to the pool and marks the victim for rollback/restart.
     */
    public RecoveryResult preempt(long processId, long resourceId, int instances) {
        requireSimulation();
        SimProcess victim = engine.findProcess(processId)
                .orElseThrow(() -> new ValidationException("Unknown process"));
        var resource = engine.findResource(resourceId)
                .orElseThrow(() -> new ValidationException("Unknown resource"));
        int held = engine.allocationOf(processId, resourceId);
        if (instances <= 0 || instances > held) {
            throw new ValidationException(victim.getName() + " holds only " + held
                    + " instance(s) of " + resource.getName());
        }
        DetectionResult before = detectionService.detect();

        Db.inTransaction(conn -> {
            AllocationService.persistRelease(conn, simulationService, processId,
                    resourceId, instances);
            simulationService.processRepository().updateState(conn, processId,
                    ProcessState.READY);
            simulationService.eventRepository().insert(conn,
                    simulationService.newEvent(EventType.RECOVERY,
                            "Recovery: preempted " + instances + " x " + resource.getName()
                                    + " from " + victim.getName()));
            return null;
        });

        engine.applyRelease(processId, resourceId, instances);
        engine.setProcessState(processId, ProcessState.READY);
        engine.bumpStep();
        engine.fireChange(simulationService.newEvent(EventType.RECOVERY,
                "Preempted " + instances + " x " + resource.getName()
                        + " from " + victim.getName() + " (victim marked for rollback)"));
        new AllocationService(engine, simulationService).tryGrantPending();

        DetectionResult verified = detectionService.detect();
        RecoveryResult result = buildResult(RecoveryStrategy.PREEMPT_RESOURCE,
                "Preempt " + instances + " x " + resource.getName()
                        + " from " + victim.getName(), before, verified);
        persistLog(result.getStrategy(), result.getTargetDescription(), result);
        return result;
    }

    /**
     * Stores the recovery log after verification, so success and the result
     * summary reflect the verified outcome.
     */
    private void persistLog(RecoveryStrategy strategy, String target,
                            RecoveryResult result) {
        Db.inTransaction(conn -> {
            RecoveryLog log = new RecoveryLog();
            log.setSimulationId(engine.getSimulation().getId());
            log.setStrategy(strategy);
            log.setTargetDescription(target);
            log.setResultSummary(result.getSummary());
            log.setSuccess(result.isSuccess());
            recoveryLogRepository.insert(conn, log);
            return null;
        });
    }

    private RecoveryResult buildResult(RecoveryStrategy strategy, String target,
                                       DetectionResult before, DetectionResult after) {
        boolean resolved = !after.isDeadlockDetected();
        String summary = resolved
                ? "Deadlock resolved. Deadlocked before: " + before.getDeadlockedProcessIds()
                  + "; deadlocked after recovery: none."
                : "Deadlock still present after recovery. Deadlocked processes: "
                  + after.getDeadlockedProcessIds();
        if (strategy == RecoveryStrategy.PREEMPT_RESOURCE) {
            summary += " Note: the victim process was marked READY and requires a "
                    + "rollback/restart, which is why preemption is a simulation.";
        }
        return new RecoveryResult(strategy, target, resolved,
                before.getDeadlockedProcessIds(), after.getDeadlockedProcessIds(), summary);
    }

    private void requireSimulation() {
        if (!engine.hasSimulation()) {
            throw new ValidationException("Create or open a simulation first");
        }
    }
}
