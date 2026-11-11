package com.paradox.service;

import com.paradox.database.Db;
import com.paradox.model.Allocation;
import com.paradox.model.EventType;
import com.paradox.model.Resource;
import com.paradox.model.ResourceRequest;
import com.paradox.model.SimProcess;
import com.paradox.model.Simulation;
import com.paradox.model.SimulationEvent;
import com.paradox.model.SimulationStatus;
import com.paradox.repository.AllocationRepository;
import com.paradox.repository.EventRepository;
import com.paradox.repository.MaxClaimRepository;
import com.paradox.repository.ProcessRepository;
import com.paradox.repository.RequestRepository;
import com.paradox.repository.ResourceRepository;
import com.paradox.repository.SimulationRepository;
import com.paradox.util.ValidationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates, opens and deletes simulations and keeps the engine synchronized
 * with the database. Opening a saved simulation loads every child row and
 * restores the full state so a user can continue or replay it.
 */
public class SimulationService {

    private final SimulationEngine engine;
    private final SimulationRepository simulationRepository = new SimulationRepository();
    private final ProcessRepository processRepository = new ProcessRepository();
    private final ResourceRepository resourceRepository = new ResourceRepository();
    private final AllocationRepository allocationRepository = new AllocationRepository();
    private final RequestRepository requestRepository = new RequestRepository();
    private final MaxClaimRepository maxClaimRepository = new MaxClaimRepository();
    private final EventRepository eventRepository = new EventRepository();

    public SimulationService(SimulationEngine engine) {
        this.engine = engine;
    }

    /** Creates and persists a new simulation, making it the open one. */
    public Simulation createSimulation(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Simulation name must not be empty");
        }
        Simulation simulation = new Simulation(name.trim(), description);
        Db.inTransaction(conn -> {
            simulationRepository.insert(conn, simulation);
            SimulationEvent created = new SimulationEvent(simulation.getId(),
                    EventType.SIMULATION_CREATED,
                    "Simulation '" + simulation.getName() + "' created");
            eventRepository.insert(conn, created);
            return null;
        });
        engine.load(simulation, List.of(), List.of(), List.of(), List.of(), Map.of());
        fire(EventType.SIMULATION_CREATED,
                "Simulation '" + simulation.getName() + "' created");
        return simulation;
    }

    /** Loads a saved simulation and all of its children into the engine. */
    public Simulation openSimulation(long simulationId) {
        return Db.inTransaction(conn -> {
            Simulation simulation = simulationRepository.findById(conn, simulationId)
                    .orElseThrow(() -> new ValidationException(
                            "Simulation " + simulationId + " does not exist"));
            List<SimProcess> processes = processRepository.findBySimulation(conn, simulationId);
            List<Resource> resources = resourceRepository.findBySimulation(conn, simulationId);
            List<Allocation> allocations = allocationRepository.findBySimulation(conn, simulationId);
            List<ResourceRequest> requests = requestRepository.findBySimulation(conn, simulationId);

            Map<Long, Map<Long, Integer>> claims = new HashMap<>();
            maxClaimRepository.findBySimulation(conn, simulationId)
                    .forEach(max -> claims.computeIfAbsent(max.getProcessId(), k -> new HashMap<>())
                            .put(max.getResourceId(), max.getMaxInstances()));

            engine.load(simulation, processes, resources, allocations, requests, claims);
            return simulation;
        });
    }

    /** Newest simulations first, for the header combo and history views. */
    public List<Simulation> listRecent(int limit) {
        List<Simulation> all = Db.inTransaction(conn -> simulationRepository.findAll(conn));
        return all.size() <= limit ? all : all.subList(0, limit);
    }

    /** Deletes a simulation; the database cascades to all child tables. */
    public void deleteSimulation(long simulationId) {
        boolean wasOpen = engine.hasSimulation()
                && engine.getSimulation().getId() == simulationId;
        Db.inTransaction(conn -> {
            simulationRepository.delete(conn, simulationId);
            return null;
        });
        if (wasOpen) {
            engine.clear();
        }
        // Built with the explicit id: the engine may have no open simulation now.
        SimulationEvent deleted = new SimulationEvent(simulationId,
                EventType.SIMULATION_DELETED, "Simulation " + simulationId + " deleted");
        engine.fireChange(deleted);
    }

    public void setStatus(SimulationStatus status) {
        if (!engine.hasSimulation()) {
            throw new ValidationException("No simulation is open");
        }
        Simulation simulation = engine.getSimulation();
        simulation.setStatus(status);
        Db.inTransaction(conn -> {
            simulationRepository.updateStatus(conn, simulation.getId(), status);
            return null;
        });
    }

    /** Builds an event for the open simulation (not yet persisted). */
    public SimulationEvent newEvent(EventType type, String message) {
        return newEvent(type, message, null);
    }

    /** Builds an event with a structured JSON payload (not yet persisted). */
    public SimulationEvent newEvent(EventType type, String message, String eventDataJson) {
        SimulationEvent event = new SimulationEvent(
                engine.getSimulation().getId(), type, message);
        event.setEventDataJson(eventDataJson);
        event.setStepNumber(engine.getStepNumber());
        return event;
    }

    private void fire(EventType type, String message) {
        SimulationEvent event = new SimulationEvent(
                engine.getSimulation().getId(), type, message);
        engine.fireChange(event);
    }

    // Exposed for sibling services so they share one engine/repository set.
    public SimulationEngine engine() {
        return engine;
    }

    public EventRepository eventRepository() {
        return eventRepository;
    }

    public ProcessRepository processRepository() {
        return processRepository;
    }

    public ResourceRepository resourceRepository() {
        return resourceRepository;
    }

    public AllocationRepository allocationRepository() {
        return allocationRepository;
    }

    public RequestRepository requestRepository() {
        return requestRepository;
    }

    public MaxClaimRepository maxClaimRepository() {
        return maxClaimRepository;
    }

    public SimulationRepository simulationRepository() {
        return simulationRepository;
    }
}
