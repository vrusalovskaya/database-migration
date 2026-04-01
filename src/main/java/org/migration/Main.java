package org.migration;

import java.io.IOException;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        MigrationService service = new MigrationService(new MigrationRepository());
        try {
            service.migrate();
        } catch (SQLException e) {
            System.out.println("Error while accessing database.");
        } catch (IOException e) {
            System.out.println("Error while working with migration files");
        }
    }
}