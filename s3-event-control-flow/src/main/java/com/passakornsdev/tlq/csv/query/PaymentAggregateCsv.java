package com.passakornsdev.tlq.csv.query;

import java.math.BigDecimal;

final class PaymentAggregateCsv {
    private PaymentAggregateCsv() {}

    static String render(PaymentAggregateResponse response) {
        StringBuilder csv = new StringBuilder();
        csv.append("overall\n");
        csv.append("record_count,total_amount\n");
        append(csv, response.overall().recordCount(), response.overall().totalAmountOfPaymentsUsdollars());
        csv.append('\n');

        csv.append("by_recipient_state\n");
        csv.append("recipient_state,record_count,total_amount\n");
        for (RecipientStatePaymentAggregate aggregate : response.byRecipientState()) {
            append(
                csv,
                aggregate.recipientState(),
                aggregate.recordCount(),
                aggregate.totalAmountOfPaymentsUsdollars()
            );
        }
        csv.append('\n');

        csv.append("by_teaching_hospital_name\n");
        csv.append("teaching_hospital_name,record_count,total_amount\n");
        for (TeachingHospitalPaymentAggregate aggregate : response.byTeachingHospitalName()) {
            append(
                csv,
                aggregate.teachingHospitalName(),
                aggregate.recordCount(),
                aggregate.totalAmountOfPaymentsUsdollars()
            );
        }
        return csv.toString();
    }

    private static void append(StringBuilder csv, long recordCount, BigDecimal totalAmount) {
        csv.append(recordCount).append(',').append(totalAmount.toPlainString()).append('\n');
    }

    private static void append(StringBuilder csv, String groupingValue, long recordCount, BigDecimal totalAmount) {
        csv.append(quote(groupingValue))
            .append(',')
            .append(recordCount)
            .append(',')
            .append(totalAmount.toPlainString())
            .append('\n');
    }

    private static String quote(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
