package org.migration;

import java.io.IOException;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        MigrationService service = new MigrationService(new MigrationRepository());
        try {
            service.migrate();
        } catch (SQLException | IOException | InterruptedException e) {
            System.out.println(e.getMessage());
        }
    }
}