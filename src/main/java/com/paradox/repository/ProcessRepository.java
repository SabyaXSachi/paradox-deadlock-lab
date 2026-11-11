package com.paradox.repository;

import com.paradox.database.DatabaseException;
import com.paradox.model.ProcessState;
import com.paradox.model.SimProcess;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** DAO for the processes table. */
public class ProcessRepository {

    public SimProcess insert(Connection connection, SimProcess process) {
        String sql = "INSERT INTO processes (simulation_id, process_name, arrival_time, "
                + "priority, state) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, process.getSimulationId());
            ps.setString(2, process.getName());
            ps.setInt(3, process.getArrivalTime());
            ps.setInt(4, process.getPriority());
            ps.setString(5, process.getState().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    process.setId(keys.getLong(1));
                }
            }
            return process;
        } catch (SQLException e) {
            throw new DatabaseException("Could not insert process", e);
        }
    }

    public Optional<SimProcess> findById(Connection connection, long id) {
        String sql = "SELECT * FROM processes WHERE process_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load process " + id, e);
        }
    }

    public List<SimProcess> findBySimulation(Connection connection, long simulationId) {
        String sql = "SELECT * FROM processes WHERE simulation_id = ? "
                + "ORDER BY process_id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<SimProcess> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not list processes of simulation", e);
        }
    }

    public boolean existsByName(Connection connection, long simulationId, String name) {
        String sql = "SELECT 1 FROM processes WHERE simulation_id = ? AND process_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not check process name", e);
        }
    }

    public void updateState(Connection connection, long id, ProcessState state) {
        String sql = "UPDATE processes SET state = ? WHERE process_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, state.name());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not update process state", e);
        }
    }

    public void delete(Connection connection, long id) {
        String sql = "DELETE FROM processes WHERE process_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete process " + id, e);
        }
    }

    private SimProcess mapRow(ResultSet rs) throws SQLException {
        SimProcess process = new SimProcess();
        process.setId(rs.getLong("process_id"));
        process.setSimulationId(rs.getLong("simulation_id"));
        process.setName(rs.getString("process_name"));
        process.setArrivalTime(rs.getInt("arrival_time"));
        process.setPriority(rs.getInt("priority"));
        process.setState(ProcessState.valueOf(rs.getString("state")));
        return process;
    }
}
