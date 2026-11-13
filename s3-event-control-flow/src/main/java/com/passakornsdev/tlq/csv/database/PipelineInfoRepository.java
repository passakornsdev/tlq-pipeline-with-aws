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
                "INSERT INTO pipeline_info (total_invocations, finished_invocations) VALUES (?, 0)",
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

    public boolean markLatestFinishedAndShouldQuery() {
        try (Connection connection = databaseConfig.openConnection()) {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                PipelineProgress progress = findLatest(connection);
                if (progress == null || progress.finishedInvocations() >= progress.totalInvocations()) {
                    connection.rollback();
                    return false;
                }

                long finishedInvocations = progress.finishedInvocations() + 1;
                try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE pipeline_info SET finished_invocations = ? WHERE id = ?"
                )) {
                    statement.setLong(1, finishedInvocations);
                    statement.setLong(2, progress.id());
                    statement.executeUpdate();
                }
                connection.commit();
                return finishedInvocations == progress.totalInvocations();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update pipeline progress", exception);
        }
    }

    public void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS pipeline_info (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    total_invocations BIGINT NOT NULL,
                    finished_invocations BIGINT NOT NULL DEFAULT 0
                )
                """);
        }
    }

    private PipelineProgress findLatest(Connection connection) throws SQLException {
        try (
            PreparedStatement statement = connection.prepareStatement(
                """
                SELECT id, total_invocations, finished_invocations
                FROM pipeline_info
                ORDER BY id DESC
                LIMIT 1
                FOR UPDATE
                """
            );
            ResultSet results = statement.executeQuery()
        ) {
            if (!results.next()) {
                return null;
            }
            return new PipelineProgress(
                results.getLong("id"),
                results.getLong("total_invocations"),
                results.getLong("finished_invocations")
            );
        }
    }

    private record PipelineProgress(long id, long totalInvocations, long finishedInvocations) {}
}
