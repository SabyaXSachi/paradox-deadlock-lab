package com.paradox.repository;

import com.paradox.database.DatabaseException;
import com.paradox.model.SimulationSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** DAO for the simulation_snapshots table (JSON full-state snapshots). */
public class SnapshotRepository {

    public SimulationSnapshot insert(Connection connection, SimulationSnapshot snapshot) {
        String sql = "INSERT INTO simulation_snapshots (simulation_id, step_number, "
                + "snapshot_data) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, snapshot.getSimulationId());
            ps.setInt(2, snapshot.getStepNumber());
            ps.setString(3, snapshot.getSnapshotDataJson());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    snapshot.setId(keys.getLong(1));
                }
            }
            return snapshot;
        } catch (SQLException e) {
            throw new DatabaseException("Could not store snapshot", e);
        }
    }

    public List<SimulationSnapshot> findBySimulation(Connection connection,
                                                     long simulationId) {
        String sql = "SELECT * FROM simulation_snapshots WHERE simulation_id = ? "
                + "ORDER BY step_number";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<SimulationSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not list snapshots", e);
        }
    }

    public void deleteBySimulation(Connection connection, long simulationId) {
        String sql = "DELETE FROM simulation_snapshots WHERE simulation_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete snapshots", e);
        }
    }

    private SimulationSnapshot mapRow(ResultSet rs) throws SQLException {
        SimulationSnapshot snapshot = new SimulationSnapshot();
        snapshot.setId(rs.getLong("snapshot_id"));
        snapshot.setSimulationId(rs.getLong("simulation_id"));
        snapshot.setStepNumber(rs.getInt("step_number"));
        snapshot.setSnapshotDataJson(rs.getString("snapshot_data"));
        Timestamp at = rs.getTimestamp("created_at");
        snapshot.setCreatedAt(at == null ? null : at.toLocalDateTime());
        return snapshot;
    }
}
