package org.migration.presentation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.migration.repository.MySqlMigrationRepository;
import org.migration.service.*;
import org.migration.util.JdbcConnectionProvider;
import org.migration.util.MigrationAction;
import org.migration.util.Sha256HashCalculator;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Scanner;

public class Main {
    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) {

        if (args.length < 3) {
            System.out.println("Usage: java Main <url> <user> <password>");
            return;
        }

        String url = args[0];
        String user = args[1];
        String password = args[2];

        MigrationService service = getMigrationService(url, user, password);

        try (Scanner in = new Scanner(System.in)) {
            System.out.println("""
                    Custom migration tool
                    Please specify the required action:
                    /migrate - initiate migration process
                    /rollback - rollback last migration
                    /rollback + number - rollback to specific version
                    /end - terminate program""");
            while (true) {
                String command = in.nextLine().trim();
                if (command.equals("/migrate")) {
                    execute("Starting migration tool...", service::migrate);
                } else if (command.equals("/rollback")) {
                    execute("Rolling back the last migration...",
                            () -> service.rollback(null));
                } else if (command.startsWith("/rollback")) {
                    String version = command.substring("/rollback".length()).trim();
                    Optional<Integer> number = parse(version);
                    if (number.isPresent()) {
                        execute("Rolling back to version " + number + "...",
                                () -> service.rollback(number.get()));
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

    private static MigrationService getMigrationService(String url, String user, String password) {
        MigrationSource migrationSource =
                new FileMigrationSource("migrations", ".sql");

        return new MigrationServiceImpl(
                new MySqlMigrationRepository(),
                new JdbcConnectionProvider(url, user, password),
                migrationSource,
                new SQLMigrationParser(new Sha256HashCalculator(), migrationSource));

    }

    private static void execute(String message, MigrationAction action)
            throws SQLException, IOException, InterruptedException {
        System.out.println(message);
        try {
            action.execute();
        } catch (RuntimeException e) {
            log.error(e);
        }
    }

    private static Optional<Integer> parse(String version) {
        try {
            return Optional.of(Integer.parseInt(version));
        } catch (NumberFormatException e) {
            System.out.println("Invalid version number: " + version + " \nTry again.");
        }
        return Optional.empty();
    }
}