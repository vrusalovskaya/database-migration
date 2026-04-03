package org.migration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Scanner;

public class Main {
    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        MigrationService service = new MigrationService(new MigrationRepository());
        try (Scanner in = new Scanner(System.in)) {
            System.out.println("""
                    Custom migration tool
                    Please specify the required action:
                    /migrate - initiating migration process
                    /rollback - rollback last migration
                    /rollback + number - rollback to specific version
                    /end - terminate program""");
            while (true) {
                String command = in.nextLine().trim();
                if (command.equals("/migrate")) {
                    System.out.println("Starting migration tool...");
                    try {
                        service.migrate();
                    } catch (RuntimeException e) {
                        log.error(e);
                    }
                } else if (command.equals("/rollback")) {
                    System.out.println("Rolling back the last migration...");
                    try {
                        service.rollback(null);
                    } catch (RuntimeException e) {
                        log.error(e);
                    }
                } else if (command.startsWith("/rollback")) {
                    String version = command.substring("/rollback".length()).trim();
                    try {
                        Integer number = Integer.parseInt(version);
                        System.out.println("Rolling back to version " + number + "...");
                        try {
                            service.rollback(number);
                        } catch (RuntimeException e) {
                            log.error(e);
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid version number: " + version + " \nTry again.");
                    }
                } else if (command.equals("/end")) {
                    break;
                } else {
                    System.out.println("Invalid command. Try again.");
                }
            }
        } catch (Exception e) {
            log.error(e);
        }
    }
}