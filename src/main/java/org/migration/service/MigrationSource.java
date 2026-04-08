package org.migration.service;

import org.migration.model.Resource;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface MigrationSource {
    List<Resource> getMigrationResources() throws IOException;
    Optional<Resource> getRollbackResource(String version) throws IOException;
    String read(Resource resource) throws IOException;
}
