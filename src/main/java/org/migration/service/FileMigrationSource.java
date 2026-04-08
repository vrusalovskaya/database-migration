package org.migration.service;

import lombok.AllArgsConstructor;
import org.migration.model.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@AllArgsConstructor
public class FileMigrationSource implements MigrationSource {
    private String migrationPath;
    private String fileExtension;

    @Override
    public List<Resource> getMigrationResources() throws IOException {
        try (Stream<Path> pathStream = Files.list(Paths.get(migrationPath))) {
            return pathStream.filter(p -> p.toString().endsWith(fileExtension))
                    .filter(p -> p.getFileName().toString().startsWith("V"))
                    .map(p -> new Resource(p.getFileName().toString(), p.toString()))
                    .toList();
        }
    }

    @Override
    public Optional<Resource> getRollbackResource(String version) throws IOException {
        try (Stream<Path> pathStream = Files.list(Paths.get(migrationPath))) {
            return pathStream.filter(p -> p.toString().endsWith(fileExtension))
                    .filter(p -> p.getFileName().toString().startsWith("R"))
                    .filter(p -> p.getFileName().toString().split("__")[0].substring(1).equals(version))
                    .map(p -> new Resource(p.getFileName().toString(), p.toString()))
                    .findFirst();
        }
    }

    @Override
    public String read(Resource resource) throws IOException {
        return Files.readString(Paths.get(resource.getPath()));
    }
}
