package com.passakornsdev.tlq.csv.query;

import com.passakornsdev.tlq.csv.database.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class PaymentQueryRepository {
    private static final String AMOUNT_SUM = """
        COALESCE(SUM(
            CASE
                WHEN TRIM(`totalAmountOfPaymentUsdollars`) REGEXP '^-?[0-9]+(\\.[0-9]+)?$'
                THEN CAST(TRIM(`totalAmountOfPaymentUsdollars`) AS DECIMAL(30, 2))
                ELSE 0
            END
        ), 0)
        """;

    private final DatabaseConfig databaseConfig;

    PaymentQueryRepository(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    PaymentAggregateResponse query() {
        try (Connection connection = databaseConfig.openConnection()) {
            return new PaymentAggregateResponse(
                queryOverall(connection),
                queryByRecipientState(connection),
                queryByTeachingHospitalName(connection)
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query payment aggregates", exception);
        }
    }

    private PaymentAggregate queryOverall(Connection connection) throws SQLException {
        String sql = """
            SELECT COUNT(*) AS record_count, %s AS total_amount
            FROM payments_raw
            """.formatted(AMOUNT_SUM);
        try (
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet results = statement.executeQuery()
        ) {
            results.next();
            return new PaymentAggregate(
                results.getLong("record_count"),
                results.getBigDecimal("total_amount")
            );
        }
    }

    private List<RecipientStatePaymentAggregate> queryByRecipientState(Connection connection)
        throws SQLException {
        String sql = """
            SELECT `recipientState` AS grouping_value, COUNT(*) AS record_count, %s AS total_amount
            FROM payments_raw
            GROUP BY `recipientState`
            ORDER BY `recipientState`
            """.formatted(AMOUNT_SUM);
        try (
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet results = statement.executeQuery()
        ) {
            List<RecipientStatePaymentAggregate> aggregates = new ArrayList<>();
            while (results.next()) {
                aggregates.add(new RecipientStatePaymentAggregate(
                    results.getString("grouping_value"),
                    results.getLong("record_count"),
                    results.getBigDecimal("total_amount")
                ));
            }
            return aggregates;
        }
    }

    private List<TeachingHospitalPaymentAggregate> queryByTeachingHospitalName(Connection connection)
        throws SQLException {
        String sql = """
            SELECT `teachingHospitalName` AS grouping_value, COUNT(*) AS record_count, %s AS total_amount
            FROM payments_raw
            GROUP BY `teachingHospitalName`
            ORDER BY `teachingHospitalName`
            """.formatted(AMOUNT_SUM);
        try (
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet results = statement.executeQuery()
        ) {
            List<TeachingHospitalPaymentAggregate> aggregates = new ArrayList<>();
            while (results.next()) {
                aggregates.add(new TeachingHospitalPaymentAggregate(
                    results.getString("grouping_value"),
                    results.getLong("record_count"),
                    results.getBigDecimal("total_amount")
                ));
            }
            return aggregates;
        }
    }
}
