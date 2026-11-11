package com.passakornsdev.tlq.csv.load;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

record DatabaseConfig(String host, int port, String database, String username, String password) {
    static DatabaseConfig fromEnvironment() {
        String secretArn = required("DB_SECRET_ARN");
        int port = parsePort(required("DB_PORT"));
        try (SecretsManagerClient secretsManager = SecretsManagerClient.create()) {
            String secretString = secretsManager.getSecretValue(
                GetSecretValueRequest.builder().secretId(secretArn).build()
            ).secretString();
            JsonNode secret = new ObjectMapper().readTree(secretString);
            return new DatabaseConfig(
                required("DB_HOST"),
                port,
                required("DB_NAME"),
                requiredSecret(secret, "username"),
                requiredSecret(secret, "password")
            );
        } catch (SdkException | IOException exception) {
            throw new IllegalStateException("Unable to read database credentials from Secrets Manager", exception);
        }
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

    private static String requiredSecret(JsonNode secret, String name) {
        JsonNode value = secret.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException("Database secret is missing a non-empty " + name);
        }
        return value.textValue();
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("DB_PORT must be a number", exception);
        }
    }
}
