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
            long start = System.currentTimeMillis();
            conn.setAutoCommit(false);
            migrationRepository.createLogTable(conn);
            migrationRepository.createLockTable(conn);
            while (!migrationRepository.acquireLock(conn)) {
                if (System.currentTimeMillis() - start > 30000) {
                    log.error("Failed to acquire lock");
                    throw new RuntimeException("Failed to acquire lock");
                }
                Thread.sleep(1000);
            }
            conn.commit();
            log.info("Lock acquired");
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
                        log.info("Executing migration {}", migration.getVersion());
                        migrationRepository.executeMigration(conn, sql);
                        migrationRepository.saveMigration(conn, migration);
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
                log.info("Lock released");
            } finally {
                conn.close();
            }
        }
    }

    private List<Migration> scanMigrationFiles() throws IOException {
        List<Migration> list = new ArrayList<>();

        try (Stream<Path> pathStream = Files.list(Paths.get(MIGRATIONS_PATH))) {
            List<Path> paths = pathStream.filter(p -> p.toString().endsWith(".sql")).toList();
            for (Path path : paths) {
                String fileName = path.getFileName().toString();

                String[] parts = fileName.split("__");
                String version = parts[0].substring(1);
                String description = parts[1].replace(".sql", "");
                String checkSum = ChecksumUtil.getHash(path);
                list.add(new Migration(version, description, fileName, checkSum));
            }
        }

        list.sort(Comparator.comparing(m -> Integer.parseInt(m.getVersion())));
        return list;
    }
}
