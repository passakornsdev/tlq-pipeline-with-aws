package com.passakornsdev.tlq.csv.query;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.passakornsdev.tlq.csv.database.DatabaseConfig;
import com.passakornsdev.tlq.csv.saaf.Inspector;
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
        context.getLogger().log("Payment query invocation started.%n");
        Inspector inspector = new Inspector();
        inspector.inspectAll();
        context.getLogger().log("Querying payment aggregates from the database.%n");
        PaymentAggregateResponse response = repository.query();
        context.getLogger().log("Payment aggregate query finished; sending email.%n");
        emailSender.send(PaymentAggregateCsv.render(response));
        inspector.inspectAllDeltas();
        context.getLogger().log(inspector.finish().toString());
        return response;
    }
}
