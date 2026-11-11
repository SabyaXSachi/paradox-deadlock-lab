package com.paradox.repository;

import com.paradox.database.DatabaseException;
import com.paradox.model.ProcessResourceMax;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** DAO for the process_resource_max table (Banker's Algorithm claim matrix). */
public class MaxClaimRepository {

    /** Upserts one claim cell; callers pass the full maximum, not a delta. */
    public void setMax(Connection connection, long processId, long resourceId,
                       int maxInstances) {
        String sql = "INSERT INTO process_resource_max (process_id, resource_id, "
                + "max_instances) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE max_instances = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, processId);
            ps.setLong(2, resourceId);
            ps.setInt(3, maxInstances);
            ps.setInt(4, maxInstances);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not store maximum claim", e);
        }
    }

    public List<ProcessResourceMax> findByProcess(Connection connection, long processId) {
        String sql = "SELECT * FROM process_resource_max WHERE process_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, processId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ProcessResourceMax> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load claims of process", e);
        }
    }

    public List<ProcessResourceMax> findBySimulation(Connection connection,
                                                     long simulationId) {
        String sql = "SELECT m.id, m.process_id, m.resource_id, m.max_instances "
                + "FROM process_resource_max m "
                + "JOIN processes p ON p.process_id = m.process_id "
                + "WHERE p.simulation_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ProcessResourceMax> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load claims of simulation", e);
        }
    }

    public void deleteByProcess(Connection connection, long processId) {
        String sql = "DELETE FROM process_resource_max WHERE process_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, processId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete claims of process", e);
        }
    }

    private ProcessResourceMax mapRow(ResultSet rs) throws SQLException {
        ProcessResourceMax max = new ProcessResourceMax();
        max.setId(rs.getLong("id"));
        max.setProcessId(rs.getLong("process_id"));
        max.setResourceId(rs.getLong("resource_id"));
        max.setMaxInstances(rs.getInt("max_instances"));
        return max;
    }
}
