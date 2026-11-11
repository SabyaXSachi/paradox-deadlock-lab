package com.paradox.service;

import com.paradox.model.Allocation;
import com.paradox.model.ProcessState;
import com.paradox.model.Resource;
import com.paradox.model.SimProcess;
import com.paradox.model.Simulation;
import com.paradox.model.SimulationStatus;
import com.paradox.repository.EventRepository;
import com.paradox.repository.SimulationRepository;
import com.paradox.util.ValidationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end service test against the real MySQL database: creates a
 * simulation, allocates, makes processes wait, terminates one process and
 * verifies that the database, the engine and the process states stay
 * consistent. Cleans up by deleting the simulation (cascade).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ServiceIntegrationTest {

    private static SimulationEngine engine;
    private static SimulationService simulationService;
    private static ProcessService processService;
    private static ResourceService resourceService;
    private static AllocationService allocationService;
    private static long simulationId;
    private static long p1Id;
    private static long p2Id;
    private static long r1Id;
    private static long r2Id;

    @BeforeAll
    static void setUp() {
        engine = new SimulationEngine();
        simulationService = new SimulationService(engine);
        processService = new ProcessService(engine, simulationService);
        resourceService = new ResourceService(engine, simulationService);
        allocationService = new AllocationService(engine, simulationService);

        Simulation simulation = simulationService.createSimulation(
                "it-" + System.currentTimeMillis(), "service integration test");
        simulationId = simulation.getId();

        SimProcess p1 = processService.addProcess("P1", 0, 1);
        SimProcess p2 = processService.addProcess("P2", 0, 1);
        Resource r1 = resourceService.addResource("R1", 1);
        Resource r2 = resourceService.addResource("R2", 1);
        p1Id = p1.getId();
        p2Id = p2.getId();
        r1Id = r1.getId();
        r2Id = r2.getId();
    }

    @AfterAll
    static void cleanUp() {
        if (simulationId > 0) {
            simulationService.deleteSimulation(simulationId);
        }
    }

    @Test
    @Order(1)
    void duplicateProcessNameIsRejected() {
        assertThrows(ValidationException.class, () -> processService.addProcess("P1", 0, 0));
    }

    @Test
    @Order(2)
    void directAllocationUpdatesDatabaseAndEngine() {
        allocationService.allocate(p1Id, r1Id, 1);
        allocationService.allocate(p2Id, r2Id, 1);

        assertEquals(0, engine.findResource(r1Id).orElseThrow().getAvailableInstances());
        List<Allocation> allocations = DbProbe.allocations(simulationId);
        assertEquals(2, allocations.size());
    }

    @Test
    @Order(3)
    void overAllocationIsRejectedByValidation() {
        assertThrows(ValidationException.class, () -> allocationService.allocate(p1Id, r2Id, 1));
    }

    @Test
    @Order(4)
    void unavailableRequestMakesProcessWaitAndPersistsPendingRow() {
        // R2 is fully allocated to P2, so P1 must wait.
        allocationService.request(p1Id, r2Id, 1);

        assertEquals(ProcessState.WAITING, engine.findProcess(p1Id).orElseThrow().getState());
        assertEquals(1, engine.getPendingRequests().size());
        assertEquals(1, DbProbe.pendingRequests(simulationId).size());
    }

    @Test
    @Order(5)
    void deletingAllocatedResourceIsBlocked() {
        assertThrows(ValidationException.class, () -> resourceService.deleteResource(r1Id));
    }

    @Test
    @Order(6)
    void terminationReleasesResourcesAndWakesWaitingProcesses() {
        // P1 waits for R2 (held by P2); P2 asks for R1 (held by P1): deadlock.
        allocationService.request(p2Id, r1Id, 1);
        assertEquals(2, engine.getPendingRequests().size());

        // Terminate P1: R1 becomes free and P2's waiting request is granted.
        processService.terminateProcess(p1Id);

        assertEquals(ProcessState.TERMINATED, engine.findProcess(p1Id).orElseThrow().getState());
        assertEquals(ProcessState.RUNNING, engine.findProcess(p2Id).orElseThrow().getState());
        assertEquals(0, engine.getPendingRequests().size());
        assertEquals(1, engine.allocationOf(p2Id, r1Id));
        assertEquals(0, engine.findResource(r1Id).orElseThrow().getAvailableInstances());
        assertEquals(0, DbProbe.pendingRequests(simulationId).size());
    }

    @Test
    @Order(7)
    void eventLogWasPersisted() {
        List<String> types = DbProbe.eventTypes(simulationId);
        assertTrue(types.contains("SIMULATION_CREATED"));
        assertTrue(types.contains("PROCESS_CREATED"));
        assertTrue(types.contains("ALLOCATION"));
        assertTrue(types.contains("REQUEST"));
        assertTrue(types.contains("PROCESS_TERMINATED"));
        assertTrue(types.contains("GRANT"));
    }

    @Test
    @Order(8)
    void statusUpdatePersists() {
        simulationService.setStatus(SimulationStatus.DEADLOCKED);
        assertEquals(SimulationStatus.DEADLOCKED,
                DbProbe.simulationStatus(simulationId));
    }

    /** Small static helper that opens its own short-lived transactions. */
    private static final class DbProbe {
        private static final EventRepository EVENTS = new EventRepository();
        private static final SimulationRepository SIMULATIONS = new SimulationRepository();

        static List<com.paradox.model.Allocation> allocations(long simId) {
            return com.paradox.database.Db.inTransaction(conn ->
                    new com.paradox.repository.AllocationRepository().findBySimulation(conn, simId));
        }

        static List<com.paradox.model.ResourceRequest> pendingRequests(long simId) {
            return com.paradox.database.Db.inTransaction(conn ->
                    new com.paradox.repository.RequestRepository()
                            .findPendingBySimulation(conn, simId));
        }

        static List<String> eventTypes(long simId) {
            return com.paradox.database.Db.inTransaction(conn ->
                    EVENTS.findBySimulation(conn, simId))
                    .stream().map(e -> e.getEventType().name()).toList();
        }

        static SimulationStatus simulationStatus(long simId) {
            return com.paradox.database.Db.inTransaction(conn ->
                    SIMULATIONS.findById(conn, simId)).orElseThrow().getStatus();
        }
    }
}
