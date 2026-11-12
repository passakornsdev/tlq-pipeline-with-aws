package com.passakornsdev.tlq.csv.load;

import com.passakornsdev.tlq.csv.transfer.PaymentRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class LoadRepository {
    private static final List<String> COLUMNS = PaymentRecord.jsonPropertyNames();

    private final DatabaseConfig databaseConfig;

    LoadRepository(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    boolean loadIfNew(String objectKey, String runId, List<PaymentRecord> records) {
        try (Connection connection = databaseConfig.openConnection()) {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                if (!claimObject(connection, objectKey)) {
                    connection.rollback();
                    return false;
                }
                insertRecords(connection, objectKey, runId, records);
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load destination object " + objectKey, exception);
        }
    }

    private void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS loaded_destination_objects (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    object_key VARCHAR(1024) NOT NULL,
                    loaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

            String columns = COLUMNS.stream()
                .map(column -> "`%s` %s".formatted(column, columnType(column)))
                .collect(Collectors.joining(",\n"));
            statement.execute("""
                CREATE TABLE IF NOT EXISTS payments_raw (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    run_id VARCHAR(1024) NOT NULL,
                    source_object_key VARCHAR(1024) NOT NULL,
                    %s,
                    loaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """.formatted(columns));
        }
    }

    private String columnType(String column) {
        return switch (column) {
            case "recipientState" -> "VARCHAR(255)";
            case "programYear" -> "VARCHAR(32)";
            case "totalAmountOfPaymentUsdollars" -> "VARCHAR(64)";
            default -> "TEXT";
        };
    }

    private boolean claimObject(Connection connection, String objectKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT IGNORE INTO loaded_destination_objects (object_key) VALUES (?)"
        )) {
            statement.setString(1, objectKey);
            return statement.executeUpdate() == 1;
        }
    }

    private void insertRecords(
        Connection connection,
        String objectKey,
        String runId,
        List<PaymentRecord> records
    ) throws SQLException {
        String columns = COLUMNS.stream().map(column -> "`%s`".formatted(column))
            .collect(Collectors.joining(", "));
        String placeholders = IntStream.range(0, COLUMNS.size() + 2)
            .mapToObj(index -> "?")
            .collect(Collectors.joining(", "));
        String sql = "INSERT INTO payments_raw (run_id, source_object_key, %s) VALUES (%s)"
            .formatted(columns, placeholders);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (PaymentRecord record : records) {
                statement.setString(1, runId);
                statement.setString(2, objectKey);
                List<String> values = record.toValues();
                for (int index = 0; index < values.size(); index++) {
                    statement.setString(index + 3, values.get(index));
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
