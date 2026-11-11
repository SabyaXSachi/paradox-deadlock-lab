package com.paradox.repository;

import com.paradox.database.DatabaseException;
import com.paradox.model.DeadlockEvent;
import com.paradox.model.DetectionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** DAO for the deadlock_events table. */
public class DeadlockEventRepository {

    public DeadlockEvent insert(Connection connection, DeadlockEvent event) {
        String sql = "INSERT INTO deadlock_events (simulation_id, detection_type, "
                + "deadlock_detected, deadlocked_processes, explanation, step_trace) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, event.getSimulationId());
            ps.setString(2, event.getDetectionType().name());
            ps.setBoolean(3, event.isDeadlockDetected());
            ps.setString(4, event.getDeadlockedProcessesJson());
            ps.setString(5, event.getExplanation());
            ps.setString(6, event.getStepTraceJson());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    event.setId(keys.getLong(1));
                }
            }
            return event;
        } catch (SQLException e) {
            throw new DatabaseException("Could not store deadlock event", e);
        }
    }

    public List<DeadlockEvent> findBySimulation(Connection connection, long simulationId) {
        String sql = "SELECT * FROM deadlock_events WHERE simulation_id = ? "
                + "ORDER BY event_id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<DeadlockEvent> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not list deadlock events", e);
        }
    }

    private DeadlockEvent mapRow(ResultSet rs) throws SQLException {
        DeadlockEvent event = new DeadlockEvent();
        event.setId(rs.getLong("event_id"));
        event.setSimulationId(rs.getLong("simulation_id"));
        event.setDetectionType(DetectionType.valueOf(rs.getString("detection_type")));
        event.setDeadlockDetected(rs.getBoolean("deadlock_detected"));
        event.setDeadlockedProcessesJson(rs.getString("deadlocked_processes"));
        event.setExplanation(rs.getString("explanation"));
        event.setStepTraceJson(rs.getString("step_trace"));
        Timestamp at = rs.getTimestamp("detected_at");
        event.setDetectedAt(at == null ? null : at.toLocalDateTime());
        return event;
    }
}
