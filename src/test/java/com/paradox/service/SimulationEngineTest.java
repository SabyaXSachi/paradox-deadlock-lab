package com.paradox.service;

import com.paradox.model.Allocation;
import com.paradox.model.ProcessState;
import com.paradox.model.Resource;
import com.paradox.model.ResourceRequest;
import com.paradox.model.RequestStatus;
import com.paradox.model.SimProcess;
import com.paradox.model.Simulation;
import com.paradox.model.SimulationEvent;
import com.paradox.util.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the in-memory simulation state. No database, no JavaFX.
 */
class SimulationEngineTest {

    private SimulationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SimulationEngine();
        Simulation simulation = new Simulation("test", "");
        simulation.setId(1L);
        engine.load(simulation, List.of(), List.of(), List.of(), List.of(), Map.of());

        engine.addResource(withId(new Resource(1L, "R1", 2), 10L));
        engine.addResource(withId(new Resource(1L, "R2", 1), 11L));
        engine.addProcess(withProcessId(new SimProcess(1L, "P1", 0, 0), 100L));
        engine.addProcess(withProcessId(new SimProcess(1L, "P2", 0, 0), 101L));
    }

    @Test
    void allocationUpdatesCountsAndKeepsInvariant() {
        engine.applyAllocation(100L, 10L, 1);

        assertEquals(1, engine.allocationOf(100L, 10L));
        assertEquals(1, engine.findResource(10L).orElseThrow().getAvailableInstances());
        assertEquals(1, engine.findResource(10L).orElseThrow().getAllocatedInstances());
        assertEquals(2, engine.findResource(10L).orElseThrow().getTotalInstances());
    }

    @Test
    void allocationBeyondAvailableIsRejected() {
        assertThrows(ValidationException.class,
                () -> engine.applyAllocation(100L, 10L, 3));
    }

    @Test
    void releaseReturnsInstancesToThePool() {
        engine.applyAllocation(100L, 10L, 2);
        engine.applyRelease(100L, 10L, 1);

        assertEquals(1, engine.allocationOf(100L, 10L));
        assertEquals(1, engine.findResource(10L).orElseThrow().getAvailableInstances());
    }

    @Test
    void releaseMoreThanHeldIsRejected() {
        engine.applyAllocation(100L, 10L, 1);
        assertThrows(ValidationException.class,
                () -> engine.applyRelease(100L, 10L, 2));
    }

    @Test
    void releaseWithoutAllocationIsRejected() {
        assertThrows(ValidationException.class,
                () -> engine.applyRelease(101L, 10L, 1));
    }

    @Test
    void pendingRequestAppearsInRequestMatrix() {
        ResourceRequest pending = new ResourceRequest(1L, 100L, 11L, 1);
        pending.setId(1L);
        pending.setStatus(RequestStatus.PENDING);
        engine.addRequest(pending);

        assertEquals(1, engine.getPendingRequests().size());
        // P1 waits for one instance of R2 (second resource column).
        assertEquals(1, engine.requestMatrix()[0][1]);
        assertEquals(0, engine.requestMatrix()[1][1]);
    }

    @Test
    void allocationMatrixReflectsHoldings() {
        engine.applyAllocation(100L, 10L, 2);
        engine.applyAllocation(101L, 11L, 1);

        int[][] matrix = engine.allocationMatrix();
        assertEquals(2, matrix[0][0]);   // P1 holds 2 x R1
        assertEquals(1, matrix[1][1]);   // P2 holds 1 x R2
    }

    @Test
    void terminationReleasesAllHoldingsAndSetsState() {
        engine.applyAllocation(100L, 10L, 1);
        engine.applyAllocation(100L, 11L, 1);

        engine.terminateProcess(100L);

        assertEquals(2, engine.findResource(10L).orElseThrow().getAvailableInstances());
        assertEquals(1, engine.findResource(11L).orElseThrow().getAvailableInstances());
        assertEquals(0, engine.allocationsOf(100L).size());
        assertEquals(ProcessState.TERMINATED,
                engine.findProcess(100L).orElseThrow().getState());
    }

    @Test
    void duplicateNamesAreDetected() {
        assertTrue(engine.hasProcessName("p1"));   // case-insensitive
        assertTrue(engine.hasResourceName("R2"));
    }

    @Test
    void listenersAreNotifiedWithCurrentStepNumber() {
        AtomicInteger notified = new AtomicInteger();
        engine.addListener(event -> notified.set(event.getStepNumber()));

        engine.bumpStep();
        SimulationEvent event = new SimulationEvent(1L, com.paradox.model.EventType.ALLOCATION, "x");
        engine.fireChange(event);

        assertEquals(1, notified.get());
        assertEquals(1, event.getStepNumber());
    }

    @Test
    void vectorsFollowResourceOrder() {
        engine.applyAllocation(100L, 10L, 1);  // R1 loses one instance
        int[] available = engine.availableVector();
        assertEquals(1, available[0]);         // R1
        assertEquals(1, available[1]);         // R2
    }

    private Resource withId(Resource resource, long id) {
        resource.setId(id);
        return resource;
    }

    private SimProcess withProcessId(SimProcess process, long id) {
        process.setId(id);
        return process;
    }
}
