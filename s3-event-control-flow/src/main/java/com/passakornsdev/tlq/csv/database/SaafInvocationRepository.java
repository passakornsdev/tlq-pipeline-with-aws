package com.passakornsdev.tlq.csv.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;

public final class SaafInvocationRepository {
    private static final String SCHEMA_LOCK_NAME = "saaf_invocations_schema";

    private final DatabaseConfig databaseConfig;

    public SaafInvocationRepository(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    public void ensureSchema() {
        try (Connection connection = databaseConfig.openConnection()) {
            acquireSchemaLock(connection);
            Throwable failure = null;
            try {
                ensureSchemaUnderLock(connection);
            } catch (SQLException | RuntimeException exception) {
                failure = exception;
                throw exception;
            } finally {
                try {
                    releaseSchemaLock(connection);
                } catch (SQLException exception) {
                    if (failure != null) {
                        failure.addSuppressed(exception);
                    } else {
                        throw exception;
                    }
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to create SAAF invocation schema", exception);
        }
    }

    public void record(
        String functionName,
        String awsRequestId,
        Map<String, Object> metrics,
        Throwable failure
    ) {
        try (Connection connection = databaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO saaf_invocations (
                     aws_request_id,
                     function_name,
                     saaf_version,
                     lang,
                     start_time,
                     invocations,
                     initialization_time,
                     container_uuid,
                     new_container,
                     runtime,
                     end_time,
                     s3_latency_ms,
                     total_pipeline_time_ms,
                     status,
                     error_class,
                     error_message
                 ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 """)) {
            statement.setString(1, awsRequestId);
            statement.setString(2, functionName);
            statement.setDouble(3, number(metrics, "version").doubleValue());
            statement.setString(4, text(metrics, "lang"));
            setUtcTimestamp(statement, 5, number(metrics, "startTime"));
            statement.setInt(6, number(metrics, "invocations").intValue());
            setUtcTimestamp(statement, 7, number(metrics, "initializationTime"));
            setNullableString(statement, 8, optionalText(metrics, "uuid"));
            setNullableInteger(statement, 9, optionalNumber(metrics, "newcontainer"));
            statement.setLong(10, number(metrics, "runtime").longValue());
            setUtcTimestamp(statement, 11, number(metrics, "endTime"));
            setNullableLong(statement, 12, optionalNumber(metrics, "s3LatencyMs"));
            setNullableLong(statement, 13, optionalNumber(metrics, "totalPipelineTimeMs"));
            statement.setString(14, failure == null ? "SUCCEEDED" : "FAILED");
            if (failure == null) {
                statement.setNull(15, Types.VARCHAR);
                statement.setNull(16, Types.VARCHAR);
            } else {
                statement.setString(15, failure.getClass().getName());
                statement.setString(16, failure.getMessage());
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Unable to persist SAAF metrics for Lambda invocation " + awsRequestId,
                exception
            );
        }
    }

    private void ensureSchemaUnderLock(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS saaf_invocations (
                    aws_request_id VARCHAR(128) NOT NULL PRIMARY KEY,
                    function_name VARCHAR(255) NOT NULL,
                    saaf_version DECIMAL(4, 2) NOT NULL,
                    lang VARCHAR(32) NOT NULL,
                    start_time DATETIME(3) NOT NULL,
                    invocations INT NOT NULL,
                    initialization_time DATETIME(3) NOT NULL,
                    container_uuid VARCHAR(64),
                    new_container BOOLEAN,
                    runtime BIGINT NOT NULL,
                    end_time DATETIME(3) NOT NULL,
                    s3_latency_ms BIGINT,
                    total_pipeline_time_ms BIGINT,
                    status VARCHAR(16) NOT NULL,
                    error_class VARCHAR(255),
                    error_message TEXT,
                    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }

        ensureColumn(connection, "s3_latency_ms", "BIGINT NULL AFTER end_time");
        dropColumnIfPresent(connection, "sqs_latency_ms");
        ensureColumn(connection, "total_pipeline_time_ms", "BIGINT NULL AFTER s3_latency_ms");
        ensureStartTimeIndex(connection);
    }

    private void ensureColumn(Connection connection, String columnName, String definition) throws SQLException {
        if (!columnExists(connection, columnName)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                    "ALTER TABLE saaf_invocations ADD COLUMN `%s` %s".formatted(columnName, definition)
                );
            } catch (SQLException exception) {
                if (!columnExists(connection, columnName)) {
                    throw exception;
                }
            }
        }
    }

    private void dropColumnIfPresent(Connection connection, String columnName) throws SQLException {
        if (columnExists(connection, columnName)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE saaf_invocations DROP COLUMN `%s`".formatted(columnName));
            } catch (SQLException exception) {
                if (columnExists(connection, columnName)) {
                    throw exception;
                }
            }
        }
    }

    private boolean columnExists(Connection connection, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
                AND table_name = 'saaf_invocations'
                AND column_name = ?
            """)) {
            statement.setString(1, columnName);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private void ensureStartTimeIndex(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
                AND table_name = 'saaf_invocations'
                AND index_name = 'idx_saaf_function_start_time'
            """);
             ResultSet results = statement.executeQuery()) {
            if (results.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                "CREATE INDEX idx_saaf_function_start_time ON saaf_invocations (function_name, start_time)"
            );
        }
    }

    private void acquireSchemaLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 30)")) {
            statement.setString(1, SCHEMA_LOCK_NAME);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next() || results.getInt(1) != 1) {
                    throw new SQLException("Unable to acquire SAAF schema migration lock");
                }
            }
        }
    }

    private void releaseSchemaLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, SCHEMA_LOCK_NAME);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next() || results.getInt(1) != 1) {
                    throw new SQLException("Unable to release SAAF schema migration lock");
                }
            }
        }
    }

    private Number number(Map<String, Object> metrics, String key) {
        Number value = optionalNumber(metrics, key);
        if (value == null) {
            throw new IllegalArgumentException("SAAF metric %s is required".formatted(key));
        }
        return value;
    }

    private Number optionalNumber(Map<String, Object> metrics, String key) {
        Object value = metrics.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalArgumentException("SAAF metric %s must be numeric".formatted(key));
    }

    private String text(Map<String, Object> metrics, String key) {
        String value = optionalText(metrics, key);
        if (value == null) {
            throw new IllegalArgumentException("SAAF metric %s is required".formatted(key));
        }
        return value;
    }

    private String optionalText(Map<String, Object> metrics, String key) {
        Object value = metrics.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("SAAF metric %s must be text".formatted(key));
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private void setNullableInteger(PreparedStatement statement, int index, Number value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BOOLEAN);
        } else {
            statement.setInt(index, value.intValue());
        }
    }

    private void setNullableLong(PreparedStatement statement, int index, Number value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value.longValue());
        }
    }

    private void setUtcTimestamp(PreparedStatement statement, int index, Number epochMillis) throws SQLException {
        statement.setTimestamp(
            index,
            Timestamp.from(Instant.ofEpochMilli(epochMillis.longValue())),
            Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        );
    }
}
