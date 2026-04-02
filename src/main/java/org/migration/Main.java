package org.migration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Scanner;

public class Main {
    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        MigrationService service = new MigrationService(new MigrationRepository());
        try {
            Scanner in = new Scanner(System.in);
            System.out.println("""
                    Custom migration tool
                    Please specify the required action:
                    /start - initiating migration process
                    /end - terminate program""");
            while (true) {
                String command = in.nextLine();
                if (command.equals("/start")) {
                    System.out.println("Starting migration tool...");
                    service.migrate();
                    break;
                } else if (command.equals("/end")) {
                    break;
                } else  {
                    System.out.println("Invalid command. Try again.");
                }
            }

        } catch (SQLException | IOException | InterruptedException e) {
            log.error(e);
            System.out.println(e.getMessage());
        }
    }
}