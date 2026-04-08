package org.migration.service;

import lombok.AllArgsConstructor;
import org.migration.model.Migration;
import org.migration.model.Resource;
import org.migration.util.HashCalculator;

import java.io.IOException;

@AllArgsConstructor
public class SQLMigrationParser implements MigrationParser {
    private HashCalculator hashCalculator;
    private MigrationSource migrationSource;

    @Override
    public Migration parse(Resource resource) throws IOException {
        String[] parts = resource.getName().split("__");
        String version = parts[0].substring(1);
        String description = parts[1].replace(".sql", "");
        String checkSum = hashCalculator.calculate(migrationSource.read(resource));

        return new Migration(version, description, resource, checkSum);
    }
}
