package com.paradox.repository;

import com.paradox.database.DatabaseException;
import com.paradox.model.Simulation;
import com.paradox.model.SimulationStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** DAO for the simulations table. */
public class SimulationRepository {

    public Simulation insert(Connection connection, Simulation simulation) {
        String sql = "INSERT INTO simulations (name, description, status) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, simulation.getName());
            ps.setString(2, simulation.getDescription());
            ps.setString(3, simulation.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    simulation.setId(keys.getLong(1));
                }
            }
            return simulation;
        } catch (SQLException e) {
            throw new DatabaseException("Could not insert simulation", e);
        }
    }

    public Optional<Simulation> findById(Connection connection, long id) {
        String sql = "SELECT * FROM simulations WHERE simulation_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load simulation " + id, e);
        }
    }

    /** All simulations, newest first - used by the history view and dashboard. */
    public List<Simulation> findAll(Connection connection) {
        String sql = "SELECT * FROM simulations ORDER BY created_at DESC, simulation_id DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Simulation> result = new ArrayList<>();
            while (rs.next()) {
                result.add(mapRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Could not list simulations", e);
        }
    }

    public void updateDetails(Connection connection, Simulation simulation) {
        String sql = "UPDATE simulations SET name = ?, description = ?, status = ? "
                + "WHERE simulation_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, simulation.getName());
            ps.setString(2, simulation.getDescription());
            ps.setString(3, simulation.getStatus().name());
            ps.setLong(4, simulation.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not update simulation", e);
        }
    }

    public void updateStatus(Connection connection, long id, SimulationStatus status) {
        String sql = "UPDATE simulations SET status = ? WHERE simulation_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not update simulation status", e);
        }
    }

    public void delete(Connection connection, long id) {
        // Children (processes, resources, logs...) are removed by ON DELETE CASCADE.
        String sql = "DELETE FROM simulations WHERE simulation_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete simulation " + id, e);
        }
    }

    private Simulation mapRow(ResultSet rs) throws SQLException {
        Simulation simulation = new Simulation();
        simulation.setId(rs.getLong("simulation_id"));
        simulation.setName(rs.getString("name"));
        simulation.setDescription(rs.getString("description"));
        simulation.setStatus(SimulationStatus.valueOf(rs.getString("status")));
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        simulation.setCreatedAt(created == null ? null : created.toLocalDateTime());
        simulation.setUpdatedAt(updated == null ? null : updated.toLocalDateTime());
        return simulation;
    }

    /** Helper for tests and dashboards. */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
