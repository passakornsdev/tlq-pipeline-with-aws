package com.passakornsdev.tlq.csv.load;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passakornsdev.tlq.csv.database.DatabaseConfig;
import com.passakornsdev.tlq.csv.database.PipelineInfoRepository;
import com.passakornsdev.tlq.csv.transfer.PaymentRecord;
import com.passakornsdev.tlq.csv.transfer.SqsBatchMessage;
import java.io.IOException;
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
            queryFunctionName
        );
    }

    LoadHandler(
        LambdaClient lambdaClient,
        ObjectMapper objectMapper,
        LoadRepository repository,
        PipelineInfoRepository pipelineInfoRepository,
        String queryFunctionName
    ) {
        this.lambdaClient = lambdaClient;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.pipelineInfoRepository = pipelineInfoRepository;
        this.queryFunctionName = queryFunctionName;
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            SqsBatchMessage batch = readBatch(message.getBody());
            PaymentRecord.validateHeaders(batch.header());
            List<PaymentRecord> records = batch.rows().stream().map(PaymentRecord::fromValues).toList();
            String objectIdentity = requiredMessageAttribute(message, "objectIdentity");
            String sourceKey = requiredMessageAttribute(message, "sourceKey");
            String batchIndex = requiredMessageAttribute(message, "batchIndex");
            String objectKey = "sqs/%s/%s/batch-%s/%s"
                .formatted(objectIdentity, sourceKey, batchIndex, message.getMessageId());
            String runId = objectIdentity;
            boolean loaded = repository.loadIfNew(objectKey, runId, records);
            boolean queryTriggered = loaded && pipelineInfoRepository.markLatestFinishedAndShouldQuery();
            if (queryTriggered) {
                invokeQuery();
            }
            context.getLogger().log(
                "%s %d records from %s batch %s with run_id=%s%s%n"
                    .formatted(
                        loaded ? "Loaded" : "Skipped duplicate",
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

    private void invokeQuery() {
        lambdaClient.invoke(InvokeRequest.builder()
            .functionName(queryFunctionName)
            .invocationType(InvocationType.EVENT)
            .payload(SdkBytes.fromUtf8String("{}"))
            .build());
    }
}
