package com.paradox.repository;

import com.paradox.database.DatabaseException;
import com.paradox.model.Allocation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** DAO for the resource_allocation table (current ownership rows). */
public class AllocationRepository {

    /**
     * Sets the owned instance count for one (process, resource) pair,
     * creating the row on first grant. Callers pass the full new amount,
     * not a delta, so a transaction either succeeds or fails as a whole.
     */
    public void setInstances(Connection connection, long simulationId,
                             long processId, long resourceId, int instances) {
        String sql = "INSERT INTO resource_allocation (simulation_id, process_id, "
                + "resource_id, instances) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE instances = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            ps.setLong(2, processId);
            ps.setLong(3, resourceId);
            ps.setInt(4, instances);
            ps.setInt(5, instances);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not record allocation", e);
        }
    }

    public List<Allocation> findBySimulation(Connection connection, long simulationId) {
        String sql = "SELECT * FROM resource_allocation WHERE simulation_id = ? "
                + "ORDER BY allocation_id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Allocation> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not list allocations", e);
        }
    }

    public List<Allocation> findByProcess(Connection connection, long processId) {
        String sql = "SELECT * FROM resource_allocation WHERE process_id = ? "
                + "ORDER BY resource_id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, processId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Allocation> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not list allocations of process", e);
        }
    }

    /** Removes ownership completely; used on release-to-zero and preemption. */
    public void deletePair(Connection connection, long processId, long resourceId) {
        String sql = "DELETE FROM resource_allocation WHERE process_id = ? AND resource_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, processId);
            ps.setLong(2, resourceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete allocation", e);
        }
    }

    /** Removes every allocation of one process; used on process termination. */
    public void deleteByProcess(Connection connection, long processId) {
        String sql = "DELETE FROM resource_allocation WHERE process_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, processId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete allocations of process", e);
        }
    }

    private Allocation mapRow(ResultSet rs) throws SQLException {
        Allocation allocation = new Allocation();
        allocation.setId(rs.getLong("allocation_id"));
        allocation.setSimulationId(rs.getLong("simulation_id"));
        allocation.setProcessId(rs.getLong("process_id"));
        allocation.setResourceId(rs.getLong("resource_id"));
        allocation.setInstances(rs.getInt("instances"));
        Timestamp at = rs.getTimestamp("allocated_at");
        allocation.setAllocatedAt(at == null ? null : at.toLocalDateTime());
        return allocation;
    }
}
