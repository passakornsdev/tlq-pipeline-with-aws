package com.passakornsdev.tlq.csv.load;

import com.passakornsdev.tlq.csv.database.DatabaseConfig;
import com.passakornsdev.tlq.csv.database.PipelineInfoRepository;
import com.passakornsdev.tlq.csv.transform.PaymentRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class LoadRepository {
    private static final List<String> COLUMNS = PaymentRecord.jsonPropertyNames();

    private final DatabaseConfig databaseConfig;

    LoadRepository(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    void upsert(List<String> recordUuids, String objectKey, String runId, List<PaymentRecord> records) {
        if (recordUuids.size() != records.size()) {
            throw new IllegalArgumentException("Each payment record must have a UUID");
        }

        try (Connection connection = databaseConfig.openConnection()) {
            connection.setAutoCommit(true);
            ensureSchema(connection);
            upsertRecords(connection, recordUuids, objectKey, runId, records);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to upsert SQS batch " + objectKey, exception);
        }
    }

    private void ensureSchema(Connection connection) throws SQLException {
        new PipelineInfoRepository(databaseConfig).ensureSchema(connection);
        try (Statement statement = connection.createStatement()) {
            String columns = COLUMNS.stream()
                .map(column -> "`%s` %s".formatted(column, columnType(column)))
                .collect(Collectors.joining(",\n"));
            statement.execute("""
                CREATE TABLE IF NOT EXISTS payments_raw (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    record_uuid CHAR(36) NOT NULL,
                    run_id VARCHAR(1024) NOT NULL,
                    source_object_key VARCHAR(1024) NOT NULL,
                    %s,
                    loaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_payments_raw_record_uuid (record_uuid)
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

    private void upsertRecords(
        Connection connection,
        List<String> recordUuids,
        String objectKey,
        String runId,
        List<PaymentRecord> records
    ) throws SQLException {
        String columns = COLUMNS.stream().map(column -> "`%s`".formatted(column))
            .collect(Collectors.joining(", "));
        String updates = Stream.concat(Stream.of("run_id", "source_object_key"), COLUMNS.stream())
            .map(column -> "`%s` = VALUES(`%s`)".formatted(column, column))
            .collect(Collectors.joining(", "));
        String placeholders = IntStream.range(0, COLUMNS.size() + 3)
            .mapToObj(index -> "?")
            .collect(Collectors.joining(", "));
        String sql = """
            INSERT INTO payments_raw (record_uuid, run_id, source_object_key, %s)
            VALUES (%s)
            ON DUPLICATE KEY UPDATE %s, loaded_at = CURRENT_TIMESTAMP
            """.formatted(columns, placeholders, updates);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int rowIndex = 0; rowIndex < records.size(); rowIndex++) {
                PaymentRecord record = records.get(rowIndex);
                statement.setString(1, recordUuids.get(rowIndex));
                statement.setString(2, runId);
                statement.setString(3, objectKey);
                List<String> values = record.toValues();
                for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                    statement.setString(valueIndex + 4, values.get(valueIndex));
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
