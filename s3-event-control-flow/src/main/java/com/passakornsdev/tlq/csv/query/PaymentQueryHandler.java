package com.passakornsdev.tlq.csv.query;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.passakornsdev.tlq.csv.database.DatabaseConfig;
import software.amazon.awssdk.services.ses.SesClient;

import static com.passakornsdev.tlq.csv.util.CommonValidator.requiredEnvironmentVariable;

public class PaymentQueryHandler implements RequestHandler<Object, PaymentAggregateResponse> {
    private final PaymentQueryRepository repository;
    private final PaymentAggregateEmailSender emailSender;

    public PaymentQueryHandler() {
        this(
            new PaymentQueryRepository(DatabaseConfig.fromEnvironment()),
            new PaymentAggregateEmailSender(
                SesClient.create(),
                requiredEnvironmentVariable("SES_FROM_EMAIL"),
                requiredEnvironmentVariable("SES_TO_EMAIL")
            )
        );
    }

    PaymentQueryHandler(
        PaymentQueryRepository repository,
        PaymentAggregateEmailSender emailSender
    ) {
        this.repository = repository;
        this.emailSender = emailSender;
    }

    @Override
    public PaymentAggregateResponse handleRequest(Object request, Context context) {
        PaymentAggregateResponse response = repository.query();
        emailSender.send(PaymentAggregateCsv.render(response));
        return response;
    }
}
