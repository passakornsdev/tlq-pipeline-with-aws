package com.passakornsdev.tlq.csv.query;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.RawMessage;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;

final class PaymentAggregateEmailSender {
    private static final String BOUNDARY = "payment-aggregate-report";

    private final SesClient sesClient;
    private final String sender;
    private final String recipient;

    PaymentAggregateEmailSender(SesClient sesClient, String sender, String recipient) {
        this.sesClient = sesClient;
        this.sender = sender;
        this.recipient = recipient;
    }

    void send(String csv) {
        String encodedCsv = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
            .encodeToString(csv.getBytes(StandardCharsets.UTF_8));
        String message = """
            From: %s
            To: %s
            Subject: Payment aggregate report
            MIME-Version: 1.0
            Content-Type: multipart/mixed; boundary="%s"

            --%s
            Content-Type: text/plain; charset=UTF-8

            The payment aggregate report is attached.

            --%s
            Content-Type: text/csv; name="payment-aggregates.csv"
            Content-Disposition: attachment; filename="payment-aggregates.csv"
            Content-Transfer-Encoding: base64

            %s
            --%s--
            """.formatted(sender, recipient, BOUNDARY, BOUNDARY, BOUNDARY, encodedCsv, BOUNDARY);

        sesClient.sendRawEmail(SendRawEmailRequest.builder()
            .rawMessage(RawMessage.builder()
                .data(SdkBytes.fromString(message, StandardCharsets.UTF_8))
                .build())
            .build());
    }
}
