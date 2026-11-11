package com.passakornsdev.tlq.csv.load;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

record DatabaseConfig(String host, int port, String database, String username, String password) {
    static DatabaseConfig fromEnvironment() {
        return new DatabaseConfig(
            required("DB_HOST"),
            Integer.parseInt(required("DB_PORT")),
            required("DB_NAME"),
            required("DB_USER"),
            required("DB_PASSWORD")
        );
    }

    Connection openConnection() throws SQLException {
        String url = "jdbc:mysql://%s:%d/%s?useSSL=true&requireSSL=true&serverTimezone=UTC"
            .formatted(host, port, database);
        return DriverManager.getConnection(url, username, password);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
