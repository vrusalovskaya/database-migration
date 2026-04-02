package org.migration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

public class Main {
    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        MigrationService service = new MigrationService(new MigrationRepository());
        try {
            service.migrate();
        } catch (SQLException | IOException | InterruptedException e) {
            log.error(e);
            System.out.println(e.getMessage());
        }
    }
}