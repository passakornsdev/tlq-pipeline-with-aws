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
        this(
            DatabaseConfig.fromEnvironment(),
            new PaymentAggregateEmailSender(
                SesClient.create(),
                requiredEnvironmentVariable("SES_FROM_EMAIL"),
                requiredEnvironmentVariable("SES_TO_EMAIL")
            ),
            requiredEnvironmentVariable("DISPATCHER_FUNCTION_NAME")
        );
    }

    private PaymentQueryHandler(
        DatabaseConfig databaseConfig,
        PaymentAggregateEmailSender emailSender,
        String dispatcherFunctionName
    ) {
        this(
            new PaymentQueryRepository(databaseConfig),
            emailSender,
            new SaafInvocationRecorder(databaseConfig),
            dispatcherFunctionName
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
            inspector.inspectContainer();
            Object startTimeMetric = inspector.getAttribute("startTime");
            if (!(startTimeMetric instanceof Number startTime)) {
                throw new IllegalStateException("SAAF startTime is unavailable");
            }
            dispatcherStartTimeMillis = repository.latestDispatcherStartTime(
                dispatcherFunctionName,
                startTime.longValue()
            );

            PaymentAggregateResponse response = repository.query();
            emailSender.send(PaymentAggregateCsv.render(response));
            return response;
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            invocationRecorder.record(inspector, context, failure, dispatcherStartTimeMillis);
        }
    }
}
