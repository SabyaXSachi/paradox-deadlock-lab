package com.paradox.repository;

import com.paradox.database.DatabaseException;
import com.paradox.model.Resource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** DAO for the resources table. */
public class ResourceRepository {

    public Resource insert(Connection connection, Resource resource) {
        String sql = "INSERT INTO resources (simulation_id, resource_name, total_instances, "
                + "available_instances, allocated_instances) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, resource.getSimulationId());
            ps.setString(2, resource.getName());
            ps.setInt(3, resource.getTotalInstances());
            ps.setInt(4, resource.getAvailableInstances());
            ps.setInt(5, resource.getAllocatedInstances());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    resource.setId(keys.getLong(1));
                }
            }
            return resource;
        } catch (SQLException e) {
            throw new DatabaseException("Could not insert resource", e);
        }
    }

    public Optional<Resource> findById(Connection connection, long id) {
        String sql = "SELECT * FROM resources WHERE resource_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load resource " + id, e);
        }
    }

    public List<Resource> findBySimulation(Connection connection, long simulationId) {
        String sql = "SELECT * FROM resources WHERE simulation_id = ? ORDER BY resource_id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Resource> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not list resources of simulation", e);
        }
    }

    public boolean existsByName(Connection connection, long simulationId, String name) {
        String sql = "SELECT 1 FROM resources WHERE simulation_id = ? AND resource_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not check resource name", e);
        }
    }

    /**
     * Writes the full instance accounting. The database CHECK constraint
     * re-verifies available + allocated == total, so a buggy update fails
     * loudly instead of corrupting the data.
     */
    public void updateCounts(Connection connection, long resourceId,
                             int totalInstances, int availableInstances,
                             int allocatedInstances) {
        String sql = "UPDATE resources SET total_instances = ?, available_instances = ?, "
                + "allocated_instances = ? WHERE resource_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, totalInstances);
            ps.setInt(2, availableInstances);
            ps.setInt(3, allocatedInstances);
            ps.setLong(4, resourceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Resource instance update violated the "
                    + "accounting invariant", e);
        }
    }

    public void delete(Connection connection, long id) {
        String sql = "DELETE FROM resources WHERE resource_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete resource " + id
                    + " (is it still allocated or requested?)", e);
        }
    }

    private Resource mapRow(ResultSet rs) throws SQLException {
        Resource resource = new Resource();
        resource.setId(rs.getLong("resource_id"));
        resource.setSimulationId(rs.getLong("simulation_id"));
        resource.setName(rs.getString("resource_name"));
        resource.setTotalInstances(rs.getInt("total_instances"));
        resource.setAvailableInstances(rs.getInt("available_instances"));
        resource.setAllocatedInstances(rs.getInt("allocated_instances"));
        return resource;
    }
}
