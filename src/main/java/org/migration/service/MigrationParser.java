package org.migration.service;

import org.migration.model.Migration;
import org.migration.model.Resource;

import java.io.IOException;

public interface MigrationParser {
    Migration parse(Resource resource) throws IOException;
}
