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
    private final DatabaseConfig databaseConfig;

    public SaafInvocationRepository(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    public void ensureSchema() {
        try (Connection connection = databaseConfig.openConnection()) {
            ensureSchema(connection);
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
        try (Connection connection = databaseConfig.openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
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
                    sqs_latency_ms,
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
                setNullableLong(statement, 12, optionalNumber(metrics, "sqsLatencyMs"));
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
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Unable to persist SAAF metrics for Lambda invocation " + awsRequestId,
                exception
            );
        }
    }

    private void ensureSchema(Connection connection) throws SQLException {
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
                    sqs_latency_ms BIGINT,
                    total_pipeline_time_ms BIGINT,
                    status VARCHAR(16) NOT NULL,
                    error_class VARCHAR(255),
                    error_message TEXT,
                    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_saaf_function_start_time (function_name, start_time)
                )
                """);
        }
        if (!hasLatencyColumn(connection)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE saaf_invocations ADD COLUMN sqs_latency_ms BIGINT NULL AFTER end_time");
            } catch (SQLException exception) {
                if (!hasLatencyColumn(connection)) {
                    throw exception;
                }
            }
        }
        if (!columnExists(connection, "total_pipeline_time_ms")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                    ALTER TABLE saaf_invocations
                    ADD COLUMN total_pipeline_time_ms BIGINT NULL AFTER sqs_latency_ms
                    """);
            } catch (SQLException exception) {
                if (!columnExists(connection, "total_pipeline_time_ms")) {
                    throw exception;
                }
            }
        }
        migrateEpochMillisColumn(connection, "start_time");
        migrateEpochMillisColumn(connection, "initialization_time");
        migrateEpochMillisColumn(connection, "end_time");
        ensureStartTimeIndex(connection);
    }

    private void acquireSchemaLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 30)")) {
            statement.setString(1, "saaf_invocations_schema");
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new SQLException("Unable to acquire SAAF schema migration lock");
                }
            }
        }
    }

    private void releaseSchemaLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, "saaf_invocations_schema");
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new SQLException("Unable to release SAAF schema migration lock");
                }
            }
        }
    }

    private void migrateEpochMillisColumn(Connection connection, String columnName) throws SQLException {
        String dataType = columnDataType(connection, columnName);
        String migrationColumn = columnName + "_utc_migration";
        String legacyColumn = columnName + "_epoch_ms";
        if ("datetime".equals(dataType)) {
            dropColumnIfPresent(connection, migrationColumn);
            dropColumnIfPresent(connection, legacyColumn);
            return;
        }
        if (!"bigint".equals(dataType)) {
            throw new SQLException(
                "Unexpected data type for saaf_invocations.%s: %s".formatted(columnName, dataType)
            );
        }
        if (columnExists(connection, legacyColumn)) {
            throw new SQLException("Unexpected migration column saaf_invocations.%s".formatted(legacyColumn));
        }
        if (!columnExists(connection, migrationColumn)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                    "ALTER TABLE saaf_invocations ADD COLUMN `%s` DATETIME(3) NULL"
                        .formatted(migrationColumn)
                );
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                UPDATE saaf_invocations
                SET `%s` = TIMESTAMPADD(
                    MICROSECOND,
                    `%s` * 1000,
                    '1970-01-01 00:00:00.000'
                )
                WHERE `%s` IS NULL
                """.formatted(migrationColumn, columnName, migrationColumn));
        }
        if (hasUnconvertedValues(connection, columnName, migrationColumn)) {
            throw new SQLException("Unable to convert epoch milliseconds in saaf_invocations.%s".formatted(columnName));
        }
        if ("start_time".equals(columnName)) {
            dropIndexIfPresent(connection, "idx_saaf_function_start_time");
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                ALTER TABLE saaf_invocations
                    CHANGE COLUMN `%s` `%s` BIGINT NOT NULL,
                    CHANGE COLUMN `%s` `%s` DATETIME(3) NOT NULL
                """.formatted(columnName, legacyColumn, migrationColumn, columnName));
        }
        dropColumnIfPresent(connection, legacyColumn);
    }

    private void ensureStartTimeIndex(Connection connection) throws SQLException {
        if (!indexExists(connection, "idx_saaf_function_start_time")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                    "CREATE INDEX idx_saaf_function_start_time ON saaf_invocations (function_name, start_time)"
                );
            }
        }
    }

    private boolean hasUnconvertedValues(
        Connection connection,
        String sourceColumn,
        String migrationColumn
    ) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                 SELECT 1
                 FROM saaf_invocations
                 WHERE `%s` IS NOT NULL
                    AND (
                        `%s` IS NULL
                        OR TIMESTAMPDIFF(
                            MICROSECOND,
                            '1970-01-01 00:00:00.000',
                            `%s`
                        ) <> `%s` * 1000
                    )
                 LIMIT 1
                 """.formatted(sourceColumn, migrationColumn, migrationColumn, sourceColumn))) {
            return resultSet.next();
        }
    }

    private String columnDataType(Connection connection, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT data_type
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
                AND table_name = 'saaf_invocations'
                AND column_name = ?
            """)) {
            statement.setString(1, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private boolean columnExists(Connection connection, String columnName) throws SQLException {
        return columnDataType(connection, columnName) != null;
    }

    private void dropColumnIfPresent(Connection connection, String columnName) throws SQLException {
        if (columnExists(connection, columnName)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE saaf_invocations DROP COLUMN `%s`".formatted(columnName));
            }
        }
    }

    private boolean indexExists(Connection connection, String indexName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
                AND table_name = 'saaf_invocations'
                AND index_name = ?
            """)) {
            statement.setString(1, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void dropIndexIfPresent(Connection connection, String indexName) throws SQLException {
        if (indexExists(connection, indexName)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP INDEX `%s` ON saaf_invocations".formatted(indexName));
            }
        }
    }

    private boolean hasLatencyColumn(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
                AND table_name = 'saaf_invocations'
                AND column_name = 'sqs_latency_ms'
            """);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next();
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
