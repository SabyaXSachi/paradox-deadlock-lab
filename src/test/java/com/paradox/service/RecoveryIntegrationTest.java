package com.paradox.service;

import com.paradox.algorithm.DetectionResult;
import com.paradox.model.ProcessState;
import com.paradox.model.RecoveryLog;
import com.paradox.model.RecoveryResult;
import com.paradox.model.Resource;
import com.paradox.model.SimProcess;
import com.paradox.model.Simulation;
import com.paradox.repository.RecoveryLogRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recovery Engine integration test against the real MySQL database.
 * Builds the three-process circular wait, verifies detection, recovers by
 * terminating one process, and verifies the deadlock is resolved.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecoveryIntegrationTest {

    private static SimulationEngine engine;
    private static SimulationService simulationService;
    private static ProcessService processService;
    private static ResourceService resourceService;
    private static AllocationService allocationService;
    private static DetectionService detectionService;
    private static RecoveryService recoveryService;
    private static long simulationId;
    private static long p1Id;
    private static long p2Id;
    private static long p3Id;
    private static long r1Id;
    private static long r2Id;
    private static long r3Id;

    @BeforeAll
    static void setUp() {
        engine = new SimulationEngine();
        simulationService = new SimulationService(engine);
        processService = new ProcessService(engine, simulationService);
        resourceService = new ResourceService(engine, simulationService);
        allocationService = new AllocationService(engine, simulationService);
        detectionService = new DetectionService(engine, simulationService);
        recoveryService = new RecoveryService(engine, simulationService, processService);

        Simulation simulation = simulationService.createSimulation(
                "recovery-it-" + System.currentTimeMillis(), "recovery test");
        simulationId = simulation.getId();

        p1Id = processService.addProcess("P1", 0, 0).getId();
        p2Id = processService.addProcess("P2", 0, 0).getId();
        p3Id = processService.addProcess("P3", 0, 0).getId();
        r1Id = resourceService.addResource("R1", 1).getId();
        r2Id = resourceService.addResource("R2", 1).getId();
        r3Id = resourceService.addResource("R3", 1).getId();

        // The demonstration scenario from the specification:
        // P1 holds R1 wants R2; P2 holds R2 wants R3; P3 holds R3 wants R1.
        allocationService.allocate(p1Id, r1Id, 1);
        allocationService.allocate(p2Id, r2Id, 1);
        allocationService.allocate(p3Id, r3Id, 1);
        allocationService.request(p1Id, r2Id, 1);
        allocationService.request(p2Id, r3Id, 1);
        allocationService.request(p3Id, r1Id, 1);
    }

    @AfterAll
    static void cleanUp() {
        if (simulationId > 0) {
            simulationService.deleteSimulation(simulationId);
        }
    }

    @Test
    @Order(1)
    void circularWaitIsDetected() {
        DetectionResult result = detectionService.detect();
        assertTrue(result.isDeadlockDetected());
        assertEquals(List.of(p1Id, p2Id, p3Id), result.getDeadlockedProcessIds());
    }

    @Test
    @Order(2)
    void terminatingOneProcessResolvesTheDeadlock() {
        RecoveryResult result = recoveryService.terminateOne(p3Id);

        assertTrue(result.isSuccess());
        assertTrue(result.getDeadlockedAfter().isEmpty());
        assertTrue(result.getSummary().contains("resolved"));

        // Detection confirms: no deadlock anymore.
        DetectionResult verified = detectionService.detect();
        assertFalse(verified.isDeadlockDetected());
        assertEquals(ProcessState.TERMINATED,
                engine.findProcess(p3Id).orElseThrow().getState());

        // The recovery log was persisted.
        List<RecoveryLog> logs = DbProbe.recoveryLogs(simulationId);
        assertEquals(1, logs.size());
        assertEquals("TERMINATE_ONE", logs.get(0).getStrategy().name());
        assertTrue(logs.get(0).isSuccess());
    }

    @Test
    @Order(3)
    void preemptedResourceReturnsToThePool() {
        // Fresh mini-deadlock: P1 holds R1 wants R2, P2 holds R2 wants R1.
        // R2/R3 were released by earlier steps; rebuild holdings explicitly.
        long p1 = processService.addProcess("P4", 0, 0).getId();
        long p2 = processService.addProcess("P5", 0, 0).getId();
        long r1 = resourceService.addResource("R4", 1).getId();
        long r2 = resourceService.addResource("R5", 1).getId();
        allocationService.allocate(p1, r1, 1);
        allocationService.allocate(p2, r2, 1);
        allocationService.request(p1, r2, 1);
        allocationService.request(p2, r1, 1);
        assertTrue(detectionService.detect().isDeadlockDetected());

        // Preempt R2 from P2: the freed instance is immediately re-granted to
        // the waiting P1 (OS-style wakeup), which breaks the deadlock.
        RecoveryResult result = recoveryService.preempt(p2, r2, 1);
        assertTrue(result.isSuccess());
        assertEquals(1, engine.allocationOf(p1, r2),
                "waiting process received the preempted instance");
        assertEquals(ProcessState.READY, engine.findProcess(p2).orElseThrow().getState());
        assertFalse(detectionService.detect().isDeadlockDetected());
    }

    @Test
    @Order(4)
    void terminateAllRequiresADeadlock() {
        assertThrows(Exception.class, () -> recoveryService.terminateAllDeadlocked());
    }

    private static final class DbProbe {
        static List<RecoveryLog> recoveryLogs(long simId) {
            return com.paradox.database.Db.inTransaction(conn ->
                    new RecoveryLogRepository().findBySimulation(conn, simId));
        }
    }
}
