package com.paradox.repository;

import com.paradox.database.DatabaseException;
import com.paradox.model.EventType;
import com.paradox.model.SimulationEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** DAO for the simulation_events append-only log. */
public class EventRepository {

    public SimulationEvent insert(Connection connection, SimulationEvent event) {
        String sql = "INSERT INTO simulation_events (simulation_id, step_number, "
                + "event_type, message, event_data) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, event.getSimulationId());
            if (event.getStepNumber() == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, event.getStepNumber());
            }
            ps.setString(3, event.getEventType().name());
            ps.setString(4, event.getMessage());
            ps.setString(5, event.getEventDataJson());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    event.setId(keys.getLong(1));
                }
            }
            return event;
        } catch (SQLException e) {
            throw new DatabaseException("Could not store simulation event", e);
        }
    }

    /** Chronological log of one simulation, used by the history view. */
    public List<SimulationEvent> findBySimulation(Connection connection,
                                                  long simulationId) {
        String sql = "SELECT * FROM simulation_events WHERE simulation_id = ? "
                + "ORDER BY event_id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<SimulationEvent> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not list simulation events", e);
        }
    }

    /** Newest entries first, used by the dashboard "recent activity" panel. */
    public List<SimulationEvent> findRecentBySimulation(Connection connection,
                                                        long simulationId,
                                                        int limit) {
        String sql = "SELECT * FROM simulation_events WHERE simulation_id = ? "
                + "ORDER BY event_id DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<SimulationEvent> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not list recent simulation events", e);
        }
    }

    private SimulationEvent mapRow(ResultSet rs) throws SQLException {
        SimulationEvent event = new SimulationEvent();
        event.setId(rs.getLong("event_id"));
        event.setSimulationId(rs.getLong("simulation_id"));
        int step = rs.getInt("step_number");
        event.setStepNumber(rs.wasNull() ? null : step);
        event.setEventType(EventType.valueOf(rs.getString("event_type")));
        event.setMessage(rs.getString("message"));
        event.setEventDataJson(rs.getString("event_data"));
        Timestamp at = rs.getTimestamp("event_time");
        event.setEventTime(at == null ? null : at.toLocalDateTime());
        return event;
    }
}
