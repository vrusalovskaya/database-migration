package org.migration.service;

import lombok.AllArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.migration.model.Migration;
import org.migration.model.Resource;
import org.migration.repository.MigrationRepository;
import org.migration.util.ConnectionProvider;
import org.migration.util.Sleeper;

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
    private final Sleeper sleeper;

    public void migrate() throws SQLException, IOException, InterruptedException {
        Connection conn = connectionProvider.getConnection();
        try {
            conn.setAutoCommit(false);

            migrationRepository.createLogTable(conn);
            migrationRepository.createLockTable(conn);

            acquireLock(conn);

            List<Migration> migrations = getAvailableMigrations();
            List<Migration> appliedMigrations = migrationRepository.getAppliedMigrations(conn);

            for (Migration migration : migrations) {
                Optional<Migration> appliedMigration = findMigrationAmongApplied(migration.getVersion(), appliedMigrations);
                if (appliedMigration.isPresent()) {
                    if (!appliedMigration.get().getCheckSum().equals(migration.getCheckSum())) {
                        log.error("Checksum mismatch for migration {}", migration.getVersion());
                        throw new RuntimeException("Migration file checksum mismatch. Version: " + migration.getVersion());
                    }
                } else {
                    String sql = migrationSource.read(migration.getResource());
                    try {
                        log.info("Executing migration {} {}", migration.getVersion(), migration.getDescription());
                        executeAndSave(migration, conn, sql, appliedMigrations);
                        conn.commit();
                    } catch (SQLException e) {
                        log.error("Migration failed for version {}", migration.getVersion(), e);
                        conn.rollback();
                        throw e;
                    }
                }
            }
            log.info("Migrations executed");
        } finally {
            try {
                migrationRepository.releaseLock(conn);
                conn.commit();
                log.info("Lock released after migration");
            } finally {
                conn.close();
            }
        }
    }

    public void rollback(Integer targetVersion) throws SQLException, InterruptedException, IOException {
        Connection conn = connectionProvider.getConnection();
        try {
            conn.setAutoCommit(false);
            migrationRepository.createLockTable(conn);
            acquireLock(conn);

            List<Migration> appliedMigrations = loadAppliedMigrationsForRollback(conn);
            appliedMigrations.sort(Comparator.comparing(Migration::getVersion).reversed());

            int index = 0;
            Integer currentVersion = Integer.parseInt(appliedMigrations.get(index).getVersion());
            targetVersion = validateTargetVersion(targetVersion, currentVersion, appliedMigrations);

            while (!currentVersion.equals(targetVersion)) {
                String currentVersionString = currentVersion.toString();
                Resource resource = migrationSource.getRollbackResource(currentVersionString)
                        .orElseThrow(() -> new RuntimeException("No rollback file found for version " + currentVersionString));
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
                currentVersion = Integer.parseInt(appliedMigrations.get(++index).getVersion());
            }
            log.info("Rollback executed");
        } finally {
            try {
                migrationRepository.releaseLock(conn);
                conn.commit();
                log.info("Lock released after rollback");
            } finally {
                conn.close();
            }
        }
    }

    private void acquireLock(Connection conn) throws SQLException, InterruptedException {
        int count = 0;
        while (!migrationRepository.acquireLock(conn)) {
            if (count == 3) {
                throw new RuntimeException("Failed to acquire lock");
            }
            sleeper.sleep(1000);
            count++;
        }
        conn.commit();
        log.info("Lock acquired");
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
                ).sorted(Comparator.comparing(m -> Integer.parseInt(m.getVersion())))
                .toList();
    }

    private Optional<Migration> findMigrationAmongApplied(String version, List<Migration> appliedMigrations) {
        return appliedMigrations.stream()
                .filter(am -> Objects.equals(am.getVersion(), version))
                .findFirst();
    }

    private void executeAndSave(Migration migration, Connection conn, String sql, List<Migration> appliedMigrations) throws SQLException {
        migrationRepository.executeMigration(conn, sql);
        migrationRepository.saveMigration(conn, migration);
        appliedMigrations.add(migration);
    }

    private List<Migration> loadAppliedMigrationsForRollback(Connection conn) throws SQLException {
        if (!migrationRepository.checkLogTableExists(conn)) {
            throw new RuntimeException("Migration history is empty. Nothing to rollback");
        }

        List<Migration> appliedMigrations = migrationRepository.getAppliedMigrations(conn);
        if (appliedMigrations.isEmpty()) {
            throw new RuntimeException("No applied migrations found for rollback");
        }

        return appliedMigrations;
    }

    private Integer validateTargetVersion(Integer targetVersion, Integer currentVersion, List<Migration> appliedMigrations) {
        if (targetVersion == null) {
            targetVersion = Integer.parseInt(appliedMigrations.get(1).getVersion());
        } else if (targetVersion >= currentVersion) {
            throw new RuntimeException("Target version is equal to/greater than current version. Nothing to rollback");
        } else {
            Integer check = targetVersion;
            if (appliedMigrations.stream().noneMatch(ap -> Integer.parseInt(ap.getVersion()) == check)) {
                throw new RuntimeException("Target version mismatch for rollback");
            }
        }
        return targetVersion;
    }
}