package com.passakornsdev.tlq.csv.query;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.passakornsdev.tlq.csv.database.DatabaseConfig;
import com.passakornsdev.tlq.csv.database.SaafInvocationRecorder;
import com.passakornsdev.tlq.csv.saaf.Inspector;
import software.amazon.awssdk.services.ses.SesClient;

import static com.passakornsdev.tlq.csv.util.CommonValidator.requiredEnvironmentVariable;

public class PaymentQueryHandler implements RequestHandler<Object, PaymentAggregateResponse> {
    private final PaymentQueryRepository repository;
    private final PaymentAggregateEmailSender emailSender;
    private final SaafInvocationRecorder invocationRecorder;
    private final String dispatcherFunctionName;

    public PaymentQueryHandler() {
        this(DatabaseConfig.fromEnvironment());
    }

    private PaymentQueryHandler(DatabaseConfig databaseConfig) {
        this(
            new PaymentQueryRepository(databaseConfig),
            new PaymentAggregateEmailSender(
                SesClient.create(),
                requiredEnvironmentVariable("SES_FROM_EMAIL"),
                requiredEnvironmentVariable("SES_TO_EMAIL")
            ),
            new SaafInvocationRecorder(databaseConfig),
            requiredEnvironmentVariable("DISPATCHER_FUNCTION_NAME")
        );
    }

    PaymentQueryHandler(
        PaymentQueryRepository repository,
        PaymentAggregateEmailSender emailSender,
        SaafInvocationRecorder invocationRecorder,
        String dispatcherFunctionName
    ) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.invocationRecorder = invocationRecorder;
        this.dispatcherFunctionName = dispatcherFunctionName;
    }

    @Override
    public PaymentAggregateResponse handleRequest(Object request, Context context) {
        Inspector inspector = new Inspector();
        Throwable failure = null;
        Long dispatcherStartTimeMillis = null;
        try {
            Object queryStartTimeMetric = inspector.getAttribute("startTime");
            if (!(queryStartTimeMetric instanceof Number queryStartTime)) {
                throw new IllegalStateException("SAAF startTime is unavailable");
            }
            dispatcherStartTimeMillis = repository.latestDispatcherStartTime(
                dispatcherFunctionName,
                queryStartTime.longValue()
            );
            if (dispatcherStartTimeMillis == null) {
                context.getLogger().log(
                    "No SAAF start_time found for dispatcher %s; total pipeline time will be NULL.%n"
                        .formatted(dispatcherFunctionName)
                );
            }
            return processRequest(request, context, inspector);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            invocationRecorder.record(inspector, context, failure, dispatcherStartTimeMillis);
        }
    }

    private PaymentAggregateResponse processRequest(Object request, Context context, Inspector inspector) {
        context.getLogger().log("Payment query invocation started.%n");
        inspector.inspectContainer();
        context.getLogger().log("Querying payment aggregates from the database.%n");
        PaymentAggregateResponse response = repository.query();
        context.getLogger().log("Payment aggregate query finished; sending email.%n");
        emailSender.send(PaymentAggregateCsv.render(response));
        return response;
    }
}
