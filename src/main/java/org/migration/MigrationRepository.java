package org.migration;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MigrationRepository {

    public void createLogTable(Connection conn) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS migration_history (
                    id SERIAL PRIMARY KEY,
                    version VARCHAR(255),
                    description VARCHAR(255),
                    check_sum VARCHAR(256)
                );
                """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
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
                    System.out.println("Executing: " + query);
                    try {
                        stmt.execute(query);
                    } catch (SQLException e) {
                        System.out.println("FAILED QUERY: " + query);
                        System.out.println(e.getMessage());
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
}
