package org.migration.service;

import java.io.IOException;
import java.sql.SQLException;

public interface MigrationService {
    void migrate() throws SQLException, IOException, InterruptedException;

    void rollback(Integer targetVersion) throws SQLException, InterruptedException, IOException;
}
