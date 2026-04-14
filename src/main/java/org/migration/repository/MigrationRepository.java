package org.migration.repository;

import org.migration.model.Migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface MigrationRepository {
    boolean acquireLock(Connection conn) throws SQLException;

    void releaseLock(Connection conn) throws SQLException;

    void createLogTable(Connection conn) throws SQLException;

    boolean checkLogTableExists(Connection conn) throws SQLException;

    List<Migration> getAppliedMigrations(Connection conn) throws SQLException;

    void executeMigration(Connection conn, String sql) throws SQLException;

    void saveMigration(Connection conn, Migration migration) throws SQLException;

    void deleteMigration(Connection conn, String version) throws SQLException;
}