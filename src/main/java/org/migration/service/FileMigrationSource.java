package org.migration.service;

import org.migration.model.Resource;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class FileMigrationSource implements MigrationSource {
    private final URI migrationUri;
    private final String fileExtension;

    public FileMigrationSource(String resourceFolderName, String fileExtension) {
        URL url = getClass().getClassLoader().getResource(resourceFolderName);
        if (url == null) {
            throw new IllegalArgumentException("Resource folder not found: " + resourceFolderName);
        }
        try {
            this.migrationUri = url.toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        this.fileExtension = fileExtension;
    }

    @Override
    public List<Resource> getMigrationResources() throws IOException {
        return processPath(pathStream -> pathStream
                .filter(p -> p.toString().endsWith(fileExtension))
                .filter(p -> p.getFileName().toString().startsWith("V"))
                .map(p -> new Resource(p.getFileName().toString(), p.toUri().toString())).toList());
    }

    @Override
    public Optional<Resource> getRollbackResource(String version) throws IOException {
        return processPath(pathStream -> pathStream
                .filter(p -> p.toString().endsWith(fileExtension))
                .filter(p -> p.getFileName().toString().startsWith("R"))
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return name.split("__")[0].substring(1).equals(version);
                })
                .map(p -> new Resource(p.getFileName().toString(), p.toUri().toString()))
                .findFirst());
    }

    @Override
    public String read(Resource resource) throws IOException {
        URI uri = URI.create(resource.path());
        if (!uri.getScheme().equals("jar")) {
            return Files.readString(Paths.get(uri));
        }

        try (FileSystem fs = getFileSystem(uri)) {
            return Files.readString(Paths.get(uri));
        }
    }

    private <T> T processPath(Function<Stream<Path>, T> action) throws IOException {
        FileSystem fs = null;
        boolean isJar = migrationUri.getScheme().equals("jar");
        try {
            if (isJar) {
                fs = getFileSystem(migrationUri);
            }
            Path rootPath = Paths.get(migrationUri);
            try (Stream<Path> pathStream = Files.list(rootPath)) {
                return action.apply(pathStream);
            }
        } finally {
            if (isJar && fs != null && fs.isOpen()) {
                fs.close();
            }
        }
    }

    private FileSystem getFileSystem(URI uri) throws IOException {
        try {
            return FileSystems.newFileSystem(uri, Collections.emptyMap());
        } catch (FileAlreadyExistsException e) {
            return FileSystems.getFileSystem(uri);
        }
    }
}
