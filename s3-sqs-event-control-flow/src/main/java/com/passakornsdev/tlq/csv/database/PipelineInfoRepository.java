package com.passakornsdev.tlq.csv.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class PipelineInfoRepository {
    private final DatabaseConfig databaseConfig;

    public PipelineInfoRepository(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    public long createPipeline(int totalInvocations) {
        if (totalInvocations < 0) {
            throw new IllegalArgumentException("totalInvocations cannot be negative");
        }

        try (Connection connection = databaseConfig.openConnection()) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pipeline_info (total_invocations) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS
            )) {
                statement.setInt(1, totalInvocations);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new IllegalStateException("Pipeline insert did not return an ID");
                    }
                    return keys.getLong(1);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to create pipeline progress row", exception);
        }
    }

    public boolean recordSuccessfulBatchAndShouldQuery(long pipelineId, String batchUuid) {
        try (Connection connection = databaseConfig.openConnection()) {
            ensureSchema(connection);
            connection.setAutoCommit(true);
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT IGNORE INTO successful_batches (pipeline_id, batch_uuid)
                VALUES (?, ?)
                """
            )) {
                statement.setLong(1, pipelineId);
                statement.setString(2, batchUuid);
                if (statement.executeUpdate() != 1 && !hasSuccessfulBatch(connection, pipelineId, batchUuid)) {
                    throw new IllegalStateException(
                        "Could not stamp batch %s for pipeline %d".formatted(batchUuid, pipelineId)
                    );
                }
            }

            if (!allBatchesSucceeded(connection, pipelineId)) {
                return false;
            }

            try (PreparedStatement statement = connection.prepareStatement(
                """
                UPDATE pipeline_info
                SET query_dispatched = TRUE
                WHERE id = ? AND query_dispatched = FALSE
                """
            )) {
                statement.setLong(1, pipelineId);
                return statement.executeUpdate() == 1;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to record successful batch " + batchUuid, exception);
        }
    }

    public void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS pipeline_info (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    total_invocations BIGINT NOT NULL,
                    query_dispatched BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS successful_batches (
                    pipeline_id BIGINT NOT NULL,
                    batch_uuid CHAR(36) NOT NULL,
                    stamped_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (pipeline_id, batch_uuid),
                    CONSTRAINT fk_successful_batches_pipeline
                        FOREIGN KEY (pipeline_id) REFERENCES pipeline_info (id)
                )
                """);
        }
    }

    private boolean allBatchesSucceeded(Connection connection, long pipelineId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            """
            SELECT pi.total_invocations, COUNT(sb.batch_uuid) AS successful_batch_count
            FROM pipeline_info AS pi
            LEFT JOIN successful_batches AS sb ON sb.pipeline_id = pi.id
            WHERE pi.id = ?
            GROUP BY pi.id, pi.total_invocations
            """
        )) {
            statement.setLong(1, pipelineId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new IllegalStateException("Pipeline %d does not exist".formatted(pipelineId));
                }
                return results.getLong("successful_batch_count") == results.getLong("total_invocations");
            }
        }
    }

    private boolean hasSuccessfulBatch(Connection connection, long pipelineId, String batchUuid)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            """
            SELECT 1
            FROM successful_batches
            WHERE pipeline_id = ? AND batch_uuid = ?
            """
        )) {
            statement.setLong(1, pipelineId);
            statement.setString(2, batchUuid);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }
}
