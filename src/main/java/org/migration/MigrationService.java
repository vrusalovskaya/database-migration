package org.migration;

import lombok.AllArgsConstructor;

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

    private static final String MIGRATIONS_PATH = "src/main/resources/migrations";
    private final MigrationRepository migrationRepository;

    public void migrate() throws SQLException, IOException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            migrationRepository.createLogTable(conn);
            List<Migration> migrations = scanMigrationFiles();
            List<Migration> appliedMigrations = migrationRepository.getAppliedMigrations(conn);
            for (Migration migration : migrations) {
                Optional<Migration> appliedMigration = appliedMigrations.stream()
                        .filter(am -> Objects.equals(am.getVersion(), migration.getVersion()))
                        .findFirst();
                if (appliedMigration.isPresent()) {
                    if (!appliedMigration.get().getCheckSum().equals(migration.getCheckSum())) {
                        throw new RuntimeException("Migration file checksum mismatch. Version: " + migration.getVersion());
                    }
                } else {
                    String sql = Files.readString(Paths.get(MIGRATIONS_PATH, migration.getFilename()));
                    migrationRepository.executeMigration(conn, sql);
                    migrationRepository.saveMigration(conn, migration);
                }
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

        list.sort(Comparator.comparing(Migration::getVersion));
        return list;
    }
}
