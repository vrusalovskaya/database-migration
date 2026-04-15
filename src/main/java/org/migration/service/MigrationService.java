package org.migration.service;

import java.io.IOException;
import java.sql.SQLException;

public interface MigrationService {
    void migrate() throws SQLException, IOException;

    void rollback(Integer targetVersion) throws SQLException, IOException;
}
