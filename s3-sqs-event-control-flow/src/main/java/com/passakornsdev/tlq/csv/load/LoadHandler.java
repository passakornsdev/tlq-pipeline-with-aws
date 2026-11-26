package com.passakornsdev.tlq.csv.load;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passakornsdev.tlq.csv.database.DatabaseConfig;
import com.passakornsdev.tlq.csv.database.PipelineInfoRepository;
import com.passakornsdev.tlq.csv.database.SaafInvocationRecorder;
import com.passakornsdev.tlq.csv.saaf.Inspector;
import com.passakornsdev.tlq.csv.transform.PaymentRecord;
import com.passakornsdev.tlq.csv.transform.SqsBatchMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.core.SdkBytes;

import static com.passakornsdev.tlq.csv.util.CommonValidator.requiredEnvironmentVariable;

public class LoadHandler implements RequestHandler<SQSEvent, Void> {
    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;
    private final LoadRepository repository;
    private final PipelineInfoRepository pipelineInfoRepository;
    private final SaafInvocationRecorder invocationRecorder;
    private final String queryFunctionName;

    public LoadHandler() {
        this(
            LambdaClient.create(),
            new ObjectMapper(),
            DatabaseConfig.fromEnvironment(),
            requiredEnvironmentVariable("QUERY_FUNCTION_NAME")
        );
    }

    private LoadHandler(
        LambdaClient lambdaClient,
        ObjectMapper objectMapper,
        DatabaseConfig databaseConfig,
        String queryFunctionName
    ) {
        this(
            lambdaClient,
            objectMapper,
            new LoadRepository(databaseConfig),
            new PipelineInfoRepository(databaseConfig),
            new SaafInvocationRecorder(databaseConfig),
            queryFunctionName
        );
    }

    LoadHandler(
        LambdaClient lambdaClient,
        ObjectMapper objectMapper,
        LoadRepository repository,
        PipelineInfoRepository pipelineInfoRepository,
        SaafInvocationRecorder invocationRecorder,
        String queryFunctionName
    ) {
        this.lambdaClient = lambdaClient;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.pipelineInfoRepository = pipelineInfoRepository;
        this.invocationRecorder = invocationRecorder;
        this.queryFunctionName = queryFunctionName;
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        Inspector inspector = new Inspector();
        Throwable failure = null;
        try {
            return processRequest(event, context, inspector);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            invocationRecorder.record(inspector, context, failure);
        }
    }

    private Void processRequest(SQSEvent event, Context context, Inspector inspector) {
        inspector.inspectContainer();
        context.getLogger().log(
            "Loader invocation started with %d SQS message(s).%n".formatted(event.getRecords().size())
        );
        Object startTimeMetric = inspector.getAttribute("startTime");
        if (!(startTimeMetric instanceof Number startTime)) {
            throw new IllegalStateException("SAAF startTime is unavailable");
        }
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            Long sqsSentAtMillis = optionalMessageAttribute(message, "sqsSentAtMs");
            if (sqsSentAtMillis == null) {
                inspector.addAttribute("sqsLatencyMs", null);
                context.getLogger().log(
                    "SQS message %s has no send timestamp; latency will be stored as NULL.%n"
                        .formatted(message.getMessageId())
                );
            } else {
                long sqsLatencyMillis = startTime.longValue() - sqsSentAtMillis;
                inspector.addAttribute("sqsLatencyMs", sqsLatencyMillis);
                context.getLogger().log(
                    "SQS message %s SAAF startTime minus send time: %d ms.%n"
                        .formatted(message.getMessageId(), sqsLatencyMillis)
                );
            }
            context.getLogger().log("Starting SQS message %s.%n".formatted(message.getMessageId()));
            String messageBody = message.getBody();
            context.getLogger().log(
                "SQS message %s body size: %.2f KiB.%n".formatted(message.getMessageId(), messageBody.getBytes(StandardCharsets.UTF_8).length / 1024.0)
            );
            SqsBatchMessage batch = readBatch(messageBody);
            PaymentRecord.validateHeaders(batch.header());
            List<PaymentRecord> records = batch.rows().stream()
                .map(row -> PaymentRecord.fromValues(row.values()))
                .toList();
            List<String> recordUuids = batch.rows().stream()
                .map(SqsBatchMessage.Row::recordUuid)
                .toList();
            String objectIdentity = requiredMessageAttribute(message, "objectIdentity");
            String sourceKey = requiredMessageAttribute(message, "sourceKey");
            String batchIndex = requiredMessageAttribute(message, "batchIndex");
            long pipelineId = Long.parseLong(requiredMessageAttribute(message, "pipelineId"));
            String batchUuid = requiredMessageAttribute(message, "batchUuid");
            String objectKey = "sqs/%s/%s/batch-%s/%s"
                .formatted(objectIdentity, sourceKey, batchIndex, message.getMessageId());
            String runId = objectIdentity;
            context.getLogger().log(
                "Loading %d records from %s batch %s into the database.%n"
                    .formatted(records.size(), sourceKey, batchIndex)
            );
            repository.upsert(recordUuids, objectKey, runId, records);
            context.getLogger().log(
                "Database upsert finished for message %s.%n".formatted(message.getMessageId())
            );
            context.getLogger().log("Recording successful batch for message %s.%n".formatted(message.getMessageId()));
            boolean queryTriggered = pipelineInfoRepository.recordSuccessfulBatchAndShouldQuery(
                pipelineId,
                batchUuid
            );
            if (queryTriggered) {
                context.getLogger().log("Invoking query function %s.%n".formatted(queryFunctionName));
                invokeQuery();
            }
            context.getLogger().log(
                "Upserted %d records from %s batch %s with run_id=%s%s%n"
                    .formatted(
                        records.size(),
                        sourceKey,
                        batchIndex,
                        runId,
                        queryTriggered ? "; triggered aggregate query" : ""
                    )
            );
        }
        return null;
    }

    private SqsBatchMessage readBatch(String body) {
        try {
            return objectMapper.readValue(body, new TypeReference<>() {});
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Unable to decode SQS batch message",
                exception
            );
        }
    }

    private String requiredMessageAttribute(SQSEvent.SQSMessage message, String name) {
        var attribute = message.getMessageAttributes().get(name);
        if (attribute == null || attribute.getStringValue() == null || attribute.getStringValue().isBlank()) {
            throw new IllegalArgumentException("SQS message attribute %s is required".formatted(name));
        }
        return attribute.getStringValue();
    }

    private Long optionalMessageAttribute(SQSEvent.SQSMessage message, String name) {
        var attribute = message.getMessageAttributes().get(name);
        if (attribute == null || attribute.getStringValue() == null || attribute.getStringValue().isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(attribute.getStringValue());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("SQS message attribute %s must be a long integer".formatted(name), exception);
        }
    }

    private void invokeQuery() {
        lambdaClient.invoke(InvokeRequest.builder()
            .functionName(queryFunctionName)
            .invocationType(InvocationType.EVENT)
            .payload(SdkBytes.fromUtf8String("{}"))
            .build());
    }
}
