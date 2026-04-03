package org.migration;

import lombok.AllArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Stream;

@AllArgsConstructor
public class MigrationService {
    private static final Logger log = LogManager.getLogger(MigrationService.class);
    private static final String MIGRATIONS_PATH = "src/main/resources/migrations";
    private final MigrationRepository migrationRepository;

    public void migrate() throws SQLException, IOException, InterruptedException {
        Connection conn = DatabaseConfig.getConnection();
        try {
            conn.setAutoCommit(false);

            migrationRepository.createLogTable(conn);
            migrationRepository.createLockTable(conn);

            acquireLock(conn);

            List<Migration> migrations = scanMigrationFiles();
            List<Migration> appliedMigrations = migrationRepository.getAppliedMigrations(conn);
            for (Migration migration : migrations) {
                Optional<Migration> appliedMigration = appliedMigrations.stream()
                        .filter(am -> Objects.equals(am.getVersion(), migration.getVersion()))
                        .findFirst();
                if (appliedMigration.isPresent()) {
                    if (!appliedMigration.get().getCheckSum().equals(migration.getCheckSum())) {
                        log.error("Checksum mismatch for migration {}", migration.getVersion());
                        throw new RuntimeException("Migration file checksum mismatch. Version: " + migration.getVersion());
                    }
                } else {
                    String sql = Files.readString(Paths.get(MIGRATIONS_PATH, migration.getFilename()));
                    try {
                        log.info("Executing migration {} {}", migration.getVersion(), migration.getDescription());
                        migrationRepository.executeMigration(conn, sql);
                        migrationRepository.saveMigration(conn, migration);
                        appliedMigrations.add(migration);
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
        Connection conn = DatabaseConfig.getConnection();
        try {
            conn.setAutoCommit(false);
            migrationRepository.createLockTable(conn);
            acquireLock(conn);

            if (!migrationRepository.checkLogTableExists(conn)) {
                throw new RuntimeException("Migration history is empty. Nothing to rollback");
            }

            List<Migration> appliedMigrations = migrationRepository.getAppliedMigrations(conn);
            if (appliedMigrations.isEmpty()) {
                throw new RuntimeException("No applied migrations found for rollback");
            }

            appliedMigrations.sort(Comparator.comparing(Migration::getVersion).reversed());
            int index = 0;
            Integer currentVersion = Integer.parseInt(appliedMigrations.get(index).getVersion());

            if (targetVersion == null) {
                targetVersion = Integer.parseInt(appliedMigrations.get(index + 1).getVersion());
            } else if (targetVersion >= currentVersion) {
                throw new RuntimeException("Target version is equal to/greater than current version. Nothing to rollback");
            } else {
                Integer check = targetVersion;
                if (appliedMigrations.stream().noneMatch(ap -> Integer.parseInt(ap.getVersion()) == check)) {
                    throw new RuntimeException("Target version mismatch for rollback");
                }
            }

            while (!currentVersion.equals(targetVersion)) {
                String fileName = getNameOfRollbackFile(currentVersion.toString());
                String sql = Files.readString(Paths.get(MIGRATIONS_PATH, fileName));
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
        long start = System.currentTimeMillis();
        while (!migrationRepository.acquireLock(conn)) {
            if (System.currentTimeMillis() - start > 30000) {
                throw new RuntimeException("Failed to acquire lock");
            }
            Thread.sleep(1000);
        }
        conn.commit();
        log.info("Lock acquired");
    }

    private List<Migration> scanMigrationFiles() throws IOException {
        List<Migration> list = new ArrayList<>();

        List<Path> paths = getPaths("V");
        for (Path path : paths) {
            String fileName = path.getFileName().toString();

            String[] parts = fileName.split("__");
            String version = parts[0].substring(1);
            String description = parts[1].replace(".sql", "");
            String checkSum = ChecksumUtil.getHash(path);
            list.add(new Migration(version, description, fileName, checkSum));
        }

        list.sort(Comparator.comparing(m -> Integer.parseInt(m.getVersion())));
        return list;
    }

    private String getNameOfRollbackFile(String targetVersion) throws IOException {
        List<Path> paths = getPaths("R");
        for (Path path : paths) {
            String fileName = path.getFileName().toString();
            String[] parts = fileName.split("__");
            String version = parts[0].substring(1);
            if (version.equals(targetVersion)) {
                return path.getFileName().toString();
            }
        }
        throw new RuntimeException("No rollback file found for version " + targetVersion);
    }

    private List<Path> getPaths(String fileType) throws IOException {
        try (Stream<Path> pathStream = Files.list(Paths.get(MIGRATIONS_PATH))) {
            return pathStream.filter(p -> p.toString().endsWith(".sql"))
                    .filter(p -> p.getFileName().toString().startsWith(fileType))
                    .toList();
        }
    }
}
