package com.paradox.service;

import com.paradox.database.Db;
import com.paradox.model.DeadlockEvent;
import com.paradox.model.RecoveryLog;
import com.paradox.model.Simulation;
import com.paradox.model.SimulationEvent;
import com.paradox.model.SimulationSnapshot;
import com.paradox.repository.DeadlockEventRepository;
import com.paradox.repository.EventRepository;
import com.paradox.repository.RecoveryLogRepository;
import com.paradox.repository.SimulationRepository;
import com.paradox.repository.SnapshotRepository;

import java.util.List;

/**
 * Read access to everything stored about a simulation: event log, deadlock
 * events, recovery logs and replayable snapshots. Backs the History module.
 */
public class HistoryService {

    private final SimulationRepository simulationRepository = new SimulationRepository();
    private final EventRepository eventRepository = new EventRepository();
    private final DeadlockEventRepository deadlockEventRepository = new DeadlockEventRepository();
    private final RecoveryLogRepository recoveryLogRepository = new RecoveryLogRepository();
    private final SnapshotRepository snapshotRepository = new SnapshotRepository();

    public List<Simulation> allSimulations() {
        return Db.inTransaction(simulationRepository::findAll);
    }

    public List<SimulationEvent> eventsOf(long simulationId) {
        return Db.inTransaction(conn -> eventRepository.findBySimulation(conn, simulationId));
    }

    public List<DeadlockEvent> deadlockEventsOf(long simulationId) {
        return Db.inTransaction(conn ->
                deadlockEventRepository.findBySimulation(conn, simulationId));
    }

    public List<RecoveryLog> recoveryLogsOf(long simulationId) {
        return Db.inTransaction(conn ->
                recoveryLogRepository.findBySimulation(conn, simulationId));
    }

    public List<SimulationSnapshot> snapshotsOf(long simulationId) {
        return Db.inTransaction(conn ->
                snapshotRepository.findBySimulation(conn, simulationId));
    }
}
