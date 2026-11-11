package com.paradox.repository;

import com.paradox.database.DatabaseException;
import com.paradox.model.RequestStatus;
import com.paradox.model.ResourceRequest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** DAO for the resource_requests table. */
public class RequestRepository {

    public ResourceRequest insert(Connection connection, ResourceRequest request) {
        String sql = "INSERT INTO resource_requests (simulation_id, process_id, "
                + "resource_id, instances, status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, request.getSimulationId());
            ps.setLong(2, request.getProcessId());
            ps.setLong(3, request.getResourceId());
            ps.setInt(4, request.getInstances());
            ps.setString(5, request.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    request.setId(keys.getLong(1));
                }
            }
            return request;
        } catch (SQLException e) {
            throw new DatabaseException("Could not insert request", e);
        }
    }

    public List<ResourceRequest> findBySimulation(Connection connection, long simulationId) {
        String sql = "SELECT * FROM resource_requests WHERE simulation_id = ? "
                + "ORDER BY request_id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ResourceRequest> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not list requests", e);
        }
    }

    public List<ResourceRequest> findPendingBySimulation(Connection connection,
                                                         long simulationId) {
        String sql = "SELECT * FROM resource_requests WHERE simulation_id = ? "
                + "AND status = 'PENDING' ORDER BY request_id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ResourceRequest> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not list pending requests", e);
        }
    }

    public Optional<ResourceRequest> findPendingPair(Connection connection,
                                                     long processId, long resourceId) {
        String sql = "SELECT * FROM resource_requests WHERE process_id = ? "
                + "AND resource_id = ? AND status = 'PENDING'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, processId);
            ps.setLong(2, resourceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not look up pending request", e);
        }
    }

    public void updateStatus(Connection connection, long requestId, RequestStatus status) {
        String sql = "UPDATE resource_requests SET status = ? WHERE request_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, requestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not update request status", e);
        }
    }

    public void deletePendingByProcess(Connection connection, long processId) {
        String sql = "DELETE FROM resource_requests WHERE process_id = ? AND status = 'PENDING'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, processId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not clear pending requests of process", e);
        }
    }

    private ResourceRequest mapRow(ResultSet rs) throws SQLException {
        ResourceRequest request = new ResourceRequest();
        request.setId(rs.getLong("request_id"));
        request.setSimulationId(rs.getLong("simulation_id"));
        request.setProcessId(rs.getLong("process_id"));
        request.setResourceId(rs.getLong("resource_id"));
        request.setInstances(rs.getInt("instances"));
        request.setStatus(RequestStatus.valueOf(rs.getString("status")));
        Timestamp at = rs.getTimestamp("requested_at");
        request.setRequestedAt(at == null ? null : at.toLocalDateTime());
        return request;
    }
}
