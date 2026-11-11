package com.paradox.service;

import com.paradox.database.Db;
import com.paradox.model.EventType;
import com.paradox.model.ProcessState;
import com.paradox.model.SimProcess;
import com.paradox.util.ValidationException;

import java.util.List;

/**
 * Creates and terminates processes. Termination releases every resource the
 * process holds and cancels its pending requests, so both the Process
 * Manager and the Recovery Engine reuse the same logic.
 */
public class ProcessService {

    private final SimulationEngine engine;
    private final SimulationService simulationService;
    private final AllocationService allocationService;

    public ProcessService(SimulationEngine engine, SimulationService simulationService) {
        this.engine = engine;
        this.simulationService = simulationService;
        this.allocationService = new AllocationService(engine, simulationService);
    }

    public SimProcess addProcess(String name, int arrivalTime, int priority) {
        requireOpenSimulation();
        validateName(name);
        if (arrivalTime < 0) {
            throw new ValidationException("Arrival time cannot be negative");
        }
        if (priority < 0) {
            throw new ValidationException("Priority cannot be negative");
        }
        if (engine.hasProcessName(name)) {
            throw new ValidationException("A process named " + name + " already exists");
        }

        SimProcess process = new SimProcess(engine.getSimulation().getId(),
                name, arrivalTime, priority);
        Db.inTransaction(conn -> {
            simulationService.processRepository().insert(conn, process);
            simulationService.eventRepository().insert(conn,
                    simulationService.newEvent(EventType.PROCESS_CREATED,
                            "Process " + name + " created (arrival " + arrivalTime
                                    + ", priority " + priority + ")"));
            return null;
        });
        engine.addProcess(process);
        engine.fireChange(simulationService.newEvent(EventType.PROCESS_CREATED,
                "Process " + name + " created"));
        return process;
    }

    /**
     * Normal termination from the Process Manager: releases all holdings,
     * cancels pending requests, marks the process TERMINATED.
     */
    public void terminateProcess(long processId) {
        requireOpenSimulation();
        SimProcess process = requireProcess(processId);
        if (process.getState() == ProcessState.TERMINATED) {
            throw new ValidationException("Process " + process.getName()
                    + " is already terminated");
        }
        Db.inTransaction(conn -> {
            doTerminate(conn, processId);
            simulationService.eventRepository().insert(conn,
                    simulationService.newEvent(EventType.PROCESS_TERMINATED,
                            "Process " + process.getName()
                                    + " terminated; resources released"));
            return null;
        });
        engine.terminateProcess(processId);
        engine.bumpStep();
        engine.fireChange(simulationService.newEvent(EventType.PROCESS_TERMINATED,
                "Process " + process.getName() + " terminated"));

        // Freed instances may satisfy waiting processes (OS-style wakeup).
        allocationService.tryGrantPending();
    }

    /**
     * Termination body shared with the Recovery Engine so a recovery
     * operation can terminate a process inside its own transaction.
     * Every holding is released through the same accounting used by the
     * allocation screen, pending requests are dropped, state becomes
     * TERMINATED.
     */
    void doTerminate(java.sql.Connection conn, long processId) {
        var allocationRepository = simulationService.allocationRepository();
        for (var allocation : allocationRepository.findByProcess(conn, processId)) {
            AllocationService.persistRelease(conn, simulationService, processId,
                    allocation.getResourceId(), allocation.getInstances());
        }
        allocationRepository.deleteByProcess(conn, processId);
        simulationService.requestRepository().deletePendingByProcess(conn, processId);
        simulationService.processRepository().updateState(conn, processId,
                ProcessState.TERMINATED);
    }

    public List<SimProcess> getProcesses() {
        return engine.getProcesses();
    }

    private SimProcess requireProcess(long processId) {
        return engine.findProcess(processId)
                .orElseThrow(() -> new ValidationException("Unknown process " + processId));
    }

    private void requireOpenSimulation() {
        if (!engine.hasSimulation()) {
            throw new ValidationException("Create or open a simulation first");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Process name must not be empty");
        }
        if (name.length() > 20) {
            throw new ValidationException("Process name is limited to 20 characters");
        }
    }
}
