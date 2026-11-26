package com.passakornsdev.tlq.csv.database;

import com.amazonaws.services.lambda.runtime.Context;
import com.passakornsdev.tlq.csv.saaf.Inspector;
import java.util.Map;

public final class SaafInvocationRecorder {
    private final SaafInvocationRepository repository;

    public SaafInvocationRecorder(DatabaseConfig databaseConfig) {
        this(new SaafInvocationRepository(databaseConfig));
    }

    public SaafInvocationRecorder(SaafInvocationRepository repository) {
        this.repository = repository;
    }

    public void record(Inspector inspector, Context context, Throwable failure) {
        record(inspector, context, failure, null);
    }

    public void record(
        Inspector inspector,
        Context context,
        Throwable failure,
        Long dispatcherStartTimeMillis
    ) {
        Map<String, Object> metrics = inspector.finish();
        if (dispatcherStartTimeMillis != null) {
            Object endTimeMetric = metrics.get("endTime");
            if (!(endTimeMetric instanceof Number endTime)) {
                throw new IllegalStateException("SAAF endTime is unavailable");
            }
            metrics.put("totalPipelineTimeMs", endTime.longValue() - dispatcherStartTimeMillis);
        }
        context.getLogger().log(metrics.toString());

        try {
            repository.record(
                context.getFunctionName(),
                context.getAwsRequestId(),
                metrics,
                failure
            );
        } catch (RuntimeException exception) {
            context.getLogger().log(
                "Unable to persist SAAF metrics for invocation %s: %s%n"
                    .formatted(context.getAwsRequestId(), exception.getMessage())
            );
            if (failure == null) {
                throw exception;
            }
            failure.addSuppressed(exception);
        }
    }
}
