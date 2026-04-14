package org.migration.service;

import lombok.AllArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.migration.model.Migration;
import org.migration.model.Resource;
import org.migration.repository.MigrationRepository;
import org.migration.util.ConnectionProvider;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@AllArgsConstructor
public class MigrationServiceImpl implements MigrationService {
    private static final Logger log = LogManager.getLogger(MigrationServiceImpl.class);

    private final MigrationRepository migrationRepository;
    private final ConnectionProvider connectionProvider;
    private final MigrationSource migrationSource;
    private final MigrationParser migrationParser;

    public void migrate() throws SQLException, IOException {
        Connection conn = connectionProvider.getConnection();
        try {
            prepareDb(conn, true);

            List<Migration> migrations = getAvailableMigrations();
            List<Migration> appliedMigrations = migrationRepository.getAppliedMigrations(conn);

            for (Migration migration : migrations) {
                if (checkMigrationBeingAlreadyApplied(migration, appliedMigrations)) {
                    continue;
                }
                executeMigration(migration, conn);
                appliedMigrations.add(migration);
            }
            log.info("Migrations executed");
        } finally {
            migrationRepository.releaseLock(conn);
            conn.close();
        }
    }

    public void rollback(Integer targetVersion) throws SQLException, IOException {
        Connection conn = connectionProvider.getConnection();
        try {
            prepareDb(conn, false);

            List<Migration> appliedMigrations = loadAppliedMigrationsForRollback(conn);

            int index = 0;
            Integer currentVersion = Integer.parseInt(appliedMigrations.get(index).version());
            targetVersion = validateTargetVersion(targetVersion, currentVersion, appliedMigrations);

            while (!currentVersion.equals(targetVersion)) {
                executeRollback(currentVersion, conn);
                currentVersion = Integer.parseInt(appliedMigrations.get(++index).version());
            }
            log.info("Rollback executed");
        } finally {
            migrationRepository.releaseLock(conn);
            conn.close();
        }
    }

    //rename?
    private void prepareDb(Connection conn, boolean createLogTable) throws SQLException {
        conn.setAutoCommit(false);
        if (!migrationRepository.acquireLock(conn)) {
            throw new RuntimeException("Could not acquire migration lock. Another process is running.");
        }
        if (createLogTable) {
            migrationRepository.createLogTable(conn);
        }
    }

    private List<Migration> getAvailableMigrations() throws IOException {
        return migrationSource.getMigrationResources().stream()
                .map(r -> {
                            try {
                                return migrationParser.parse(r);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                ).sorted(Comparator.comparing(m -> Integer.parseInt(m.version())))
                .toList();
    }

    private boolean checkMigrationBeingAlreadyApplied(Migration migration, List<Migration> appliedMigrations) {
        Optional<Migration> appliedMigration = findMigrationAmongApplied(migration.version(), appliedMigrations);
        if (appliedMigration.isPresent()) {
            if (!appliedMigration.get().checkSum().equals(migration.checkSum())) {
                log.error("Checksum mismatch for migration {}", migration.version());
                throw new RuntimeException("Migration file checksum mismatch. Version: " + migration.version());
            }
            return true;
        }
        return false;
    }

    private Optional<Migration> findMigrationAmongApplied(String version, List<Migration> appliedMigrations) {
        return appliedMigrations.stream()
                .filter(am -> Objects.equals(am.version(), version))
                .findFirst();
    }

    private void executeMigration(Migration migration, Connection conn) throws IOException, SQLException {
        String sql = migrationSource.read(migration.resource());
        try {
            log.info("Executing migration {} {}", migration.version(), migration.description());
            migrationRepository.executeMigration(conn, sql);
            migrationRepository.saveMigration(conn, migration);
            conn.commit();
        } catch (SQLException e) {
            log.error("Migration failed for version {}", migration.version(), e);
            conn.rollback();
            throw e;
        }
    }

    private List<Migration> loadAppliedMigrationsForRollback(Connection conn) throws SQLException {
        if (!migrationRepository.checkLogTableExists(conn)) {
            throw new RuntimeException("Migration history is empty. Nothing to rollback");
        }

        List<Migration> appliedMigrations = migrationRepository.getAppliedMigrations(conn);
        if (appliedMigrations.isEmpty()) {
            throw new RuntimeException("No applied migrations found for rollback");
        }

        appliedMigrations.sort(Comparator.comparing(Migration::version).reversed());
        return appliedMigrations;
    }

    private Integer validateTargetVersion(Integer targetVersion, Integer currentVersion, List<Migration> appliedMigrations) {
        if (targetVersion == null) {
            targetVersion = Integer.parseInt(appliedMigrations.get(1).version());
        } else if (targetVersion >= currentVersion) {
            throw new RuntimeException("Target version is equal to/greater than current version. Nothing to rollback");
        } else {
            Integer check = targetVersion;
            if (appliedMigrations.stream().noneMatch(ap -> Integer.parseInt(ap.version()) == check)) {
                throw new RuntimeException("Target version mismatch for rollback");
            }
        }
        return targetVersion;
    }

    private void executeRollback(Integer currentVersion, Connection conn) throws IOException, SQLException {
        Resource resource = getRollbackResource(currentVersion);
        String sql = migrationSource.read(resource);
        try {
            log.info("Executing rollback {}", currentVersion);
            migrationRepository.executeMigration(conn, sql);
            migrationRepository.deleteMigration(conn, currentVersion.toString());
            conn.commit();
        } catch (SQLException e) {
            log.error("Rollback failed for version {}", currentVersion, e);
            conn.rollback();
            throw e;
        }
    }

    private Resource getRollbackResource(Integer currentVersion) throws IOException {
        String currentVersionString = currentVersion.toString();
        return migrationSource.getRollbackResource(currentVersionString)
                .orElseThrow(() -> new RuntimeException("No rollback file found for version " + currentVersionString));
    }
}