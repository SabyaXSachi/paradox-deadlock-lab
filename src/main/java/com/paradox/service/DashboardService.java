package com.paradox.service;

import com.paradox.database.Db;
import com.paradox.model.Simulation;
import com.paradox.model.SimulationEvent;
import com.paradox.model.SimulationStatus;
import com.paradox.repository.EventRepository;
import com.paradox.repository.SimulationRepository;

import java.util.List;

/**
 * Aggregated numbers and lists for the dashboard: global simulation count,
 * live counts of the open simulation, recent history and recent events.
 */
public class DashboardService {

    /** One dashboard snapshot; all values already formatted for display. */
    public record DashboardStats(long totalSimulations, int processCount,
                                 int resourceTypeCount, int allocatedInstances,
                                 int availableInstances, String systemStatus,
                                 boolean simulationOpen) {
    }

    private final SimulationEngine engine;

    public DashboardService(SimulationEngine engine) {
        this.engine = engine;
    }

    public DashboardStats collectStats() {
        long totalSimulations = Db.inTransaction(conn ->
                (long) new SimulationRepository().findAll(conn).size());

        if (!engine.hasSimulation()) {
            return new DashboardStats(totalSimulations, 0, 0, 0, 0,
                    "No simulation open", false);
        }
        int allocated = engine.getResources().stream()
                .mapToInt(com.paradox.model.Resource::getAllocatedInstances).sum();
        int available = engine.getResources().stream()
                .mapToInt(com.paradox.model.Resource::getAvailableInstances).sum();
        SimulationStatus status = engine.getSimulation().getStatus();
        String statusText = switch (status) {
            case CREATED -> "Ready - no deadlock check yet";
            case RUNNING -> "Running";
            case DEADLOCKED -> "DEADLOCK DETECTED";
            case RECOVERED -> "Recovered";
            case COMPLETED -> "Completed";
        };
        return new DashboardStats(totalSimulations,
                engine.getProcesses().size(),
                engine.getResources().size(),
                allocated, available, statusText, true);
    }

    /** Most recent simulations for the history panel. */
    public List<Simulation> recentSimulations(int limit) {
        List<Simulation> all = Db.inTransaction(conn ->
                new SimulationRepository().findAll(conn));
        return all.size() <= limit ? all : all.subList(0, limit);
    }

    /** Newest log entries of the open simulation. */
    public List<SimulationEvent> recentEvents(int limit) {
        if (!engine.hasSimulation()) {
            return List.of();
        }
        return Db.inTransaction(conn -> new EventRepository()
                .findRecentBySimulation(conn, engine.getSimulation().getId(), limit));
    }
}
