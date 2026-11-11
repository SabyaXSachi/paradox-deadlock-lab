package com.paradox.repository;

import com.paradox.database.DatabaseException;
import com.paradox.model.RecoveryLog;
import com.paradox.model.RecoveryStrategy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** DAO for the recovery_logs table. */
public class RecoveryLogRepository {

    public RecoveryLog insert(Connection connection, RecoveryLog log) {
        String sql = "INSERT INTO recovery_logs (simulation_id, strategy, "
                + "target_description, result_summary, success) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, log.getSimulationId());
            ps.setString(2, log.getStrategy().name());
            ps.setString(3, log.getTargetDescription());
            ps.setString(4, log.getResultSummary());
            ps.setBoolean(5, log.isSuccess());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    log.setId(keys.getLong(1));
                }
            }
            return log;
        } catch (SQLException e) {
            throw new DatabaseException("Could not store recovery log", e);
        }
    }

    public List<RecoveryLog> findBySimulation(Connection connection, long simulationId) {
        String sql = "SELECT * FROM recovery_logs WHERE simulation_id = ? "
                + "ORDER BY recovery_id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RecoveryLog> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not list recovery logs", e);
        }
    }

    private RecoveryLog mapRow(ResultSet rs) throws SQLException {
        RecoveryLog log = new RecoveryLog();
        log.setId(rs.getLong("recovery_id"));
        log.setSimulationId(rs.getLong("simulation_id"));
        log.setStrategy(RecoveryStrategy.valueOf(rs.getString("strategy")));
        log.setTargetDescription(rs.getString("target_description"));
        log.setResultSummary(rs.getString("result_summary"));
        log.setSuccess(rs.getBoolean("success"));
        Timestamp at = rs.getTimestamp("executed_at");
        log.setExecutedAt(at == null ? null : at.toLocalDateTime());
        return log;
    }
}
