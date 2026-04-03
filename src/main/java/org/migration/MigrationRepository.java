package org.migration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MigrationRepository {

    private static final Logger log = LogManager.getLogger(MigrationRepository.class);

    public void createLockTable(Connection conn) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS migration_lock (
                    id INT PRIMARY KEY,
                    locked BOOLEAN DEFAULT FALSE,
                    locked_at TIMESTAMP
                );
                """;
        String sql_insert = "INSERT IGNORE INTO migration_lock (id, locked) VALUES (1, false);";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            stmt.execute(sql_insert);
        }
    }

    public boolean acquireLock(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            int updatedRows = stmt.executeUpdate("""
                    UPDATE migration_lock
                    SET locked = true, locked_at = NOW()
                    WHERE id = 1 AND locked = false;""");
            return updatedRows > 0;
        }
    }

    public void releaseLock(Connection conn) throws SQLException {
        String sql = "UPDATE migration_lock SET locked = false, locked_at = NOW() WHERE id = 1";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
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
        try (ResultSet rs = conn.createStatement()
                .executeQuery("SHOW TABLES LIKE 'migration_history'")) {
            return rs.next();
        }
    }

    public List<Migration> getAppliedMigrations(Connection conn) throws SQLException {
        List<Migration> list = new ArrayList<>();

        try (ResultSet rs = conn.createStatement()
                .executeQuery("SELECT version, check_sum FROM migration_history")) {
            while (rs.next()) {
                Migration migration = new Migration();
                migration.setVersion(rs.getString("version"));
                migration.setCheckSum(rs.getString("check_sum"));
                list.add(migration);
            }
        }

        return list;
    }

    public void executeMigration(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            String[] queries = sql.split(";");
            for (String query : queries) {
                query = query.trim();
                if (!query.isEmpty()) {
                    log.info("Executing: {}", query);
                    try {
                        stmt.execute(query);
                    } catch (SQLException e) {
                        log.error("Error executing: {}", query, e);
                        throw e;
                    }
                }
            }
        }
    }

    public void saveMigration(Connection conn, Migration migration) throws SQLException {
        String sql = "INSERT INTO migration_history(version, description, check_sum ) VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, migration.getVersion());
            ps.setString(2, migration.getDescription());
            ps.setString(3, migration.getCheckSum());
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
}
