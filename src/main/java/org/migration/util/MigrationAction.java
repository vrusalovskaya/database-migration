package org.migration.util;

import java.io.IOException;
import java.sql.SQLException;

@FunctionalInterface
public interface MigrationAction {
    void execute() throws SQLException, IOException, InterruptedException;
}
