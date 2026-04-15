package org.migration.repository;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.migration.model.Migration;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MySqlMigrationRepository implements MigrationRepository {

    private static final Logger log = LogManager.getLogger(MySqlMigrationRepository.class);

    public boolean acquireLock(Connection conn) throws SQLException {
        String sql = "SELECT GET_LOCK('migration_lock_name', 0)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next() && rs.getInt(1) == 1) {
                log.info("Lock acquired");
                return true;
            }
           return false;
        }
    }

    public void releaseLock(Connection conn) throws SQLException {
        String sql = "SELECT RELEASE_LOCK('migration_lock_name')";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getInt(1) == 1) {
                log.info("Named lock released successfully.");
            }
        }
    }

    public void createLogTable(Connection conn) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS migration_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    version VARCHAR(255) UNIQUE NOT NULL,
                    description VARCHAR(255),
                    check_sum VARCHAR(256) NOT NULL
                );
                """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    public boolean checkLogTableExists(Connection conn) throws SQLException {
        String sql = "SHOW TABLES LIKE 'migration_history'";
        try (ResultSet rs = conn.createStatement().executeQuery(sql)) {
            return rs.next();
        }
    }

    public List<Migration> getAppliedMigrations(Connection conn) throws SQLException {
        List<Migration> list = new ArrayList<>();
        String sql = "SELECT version, check_sum FROM migration_history";
        try (ResultSet rs = conn.createStatement().executeQuery(sql)) {
            while (rs.next()) {
                Migration migration = new Migration(
                        rs.getString("version"), null, null,
                        rs.getString("check_sum"));
                list.add(migration);
            }
        }

        return list;
    }

    public void executeMigration(Connection conn, String sql) throws SQLException {
        String[] queries = sql.split(";");
        try (Statement stmt = conn.createStatement()) {
            for (String query : queries) {
                String  sanitizedQuery = query.trim();
                if (!query.isEmpty()) {
                    executeSingleQuery(sanitizedQuery, stmt);
                }
            }
        }
    }

    public void saveMigration(Connection conn, Migration migration) throws SQLException {
        String sql = "INSERT INTO migration_history(version, description, check_sum ) VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, migration.version());
            ps.setString(2, migration.description());
            ps.setString(3, migration.checkSum());
            ps.executeUpdate();
        }
    }

    public void deleteMigration(Connection conn, String version) throws SQLException {
        String sql = "DELETE FROM migration_history WHERE version = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, version);
            ps.executeUpdate();
        }
    }

    private static void executeSingleQuery(String query, Statement stmt) throws SQLException {
        try {
            log.info("Executing: {}", query);
            stmt.execute(query);
        } catch (SQLException e) {
            log.error("Error executing: {}", query, e);
            throw e;
        }
    }
}
