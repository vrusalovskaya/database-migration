package org.migration.util;

import lombok.AllArgsConstructor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@AllArgsConstructor
public class JdbcConnectionProvider implements ConnectionProvider {
    private String url;
    private String user;
    private String password;

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
