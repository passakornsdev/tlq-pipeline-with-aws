package com.passakornsdev.tlq.csv.transform;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passakornsdev.tlq.csv.database.DatabaseConfig;
import com.passakornsdev.tlq.csv.database.SaafInvocationRecorder;
import com.passakornsdev.tlq.csv.saaf.Inspector;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import static com.passakornsdev.tlq.csv.util.CommonValidator.requiredEnvironmentVariable;

public class ProcessorHandler implements RequestHandler<ProcessingRequest, Void> {
    private static final int PROCESSOR_LINE_COUNT = 100_000;
    private static final int SQS_BATCH_SIZE = 1_000;

    private final S3Client s3Client;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final CsvRecordExtractor csvRecordExtractor;
    private final String queueUrl;
    private final SaafInvocationRecorder invocationRecorder;

    public ProcessorHandler() {
        this(
            S3Client.create(),
            SqsClient.create(),
            new ObjectMapper(),
            new CsvRecordExtractor(),
            requiredEnvironmentVariable("SQS_QUEUE_URL"),
            new SaafInvocationRecorder(DatabaseConfig.fromEnvironment())
        );
    }

    ProcessorHandler(
        S3Client s3Client,
        SqsClient sqsClient,
        ObjectMapper objectMapper,
        CsvRecordExtractor csvRecordExtractor,
        String queueUrl,
        SaafInvocationRecorder invocationRecorder
    ) {
        this.s3Client = s3Client;
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.csvRecordExtractor = csvRecordExtractor;
        this.queueUrl = queueUrl;
        this.invocationRecorder = invocationRecorder;
    }

    @Override
    public Void handleRequest(ProcessingRequest request, Context context) {
        Inspector inspector = new Inspector();
        Throwable failure = null;
        try {
            return processRequest(request, context, inspector);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            invocationRecorder.record(inspector, context, failure);
        }
    }

    private Void processRequest(ProcessingRequest request, Context context, Inspector inspector) {
        validateRequest(request);
        inspector.inspectContainer();
        context.getLogger().log(
            "Processor handling data lines [%d, %d), bytes [%d, %d), from s3://%s/%s.%n"
                .formatted(
                    request.startLine(),
                    request.endExclusiveLine(),
                    request.startByte(),
                    request.endExclusiveByte(),
                    request.sourceBucket(),
                    request.sourceKey()
                )
        );

        try (
            ResponseInputStream<GetObjectResponse> headerInput = s3Client.getObject(
                rangedObjectRequest(request, 0, request.headerEndByte())
            );
            BufferedReader headerReader = new BufferedReader(
                new InputStreamReader(headerInput, StandardCharsets.UTF_8)
            )
        ) {
            csvRecordExtractor.validateHeader(headerReader);
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Unable to read CSV header from s3://%s/%s".formatted(request.sourceBucket(), request.sourceKey()),
                exception
            );
        }

        try (
            ResponseInputStream<GetObjectResponse> input = s3Client.getObject(
                rangedObjectRequest(request, request.startByte(), request.endExclusiveByte())
            );
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
        ) {
            csvRecordExtractor.extractBatches(
                reader,
                request.startLine(),
                request.endExclusiveLine(),
                SQS_BATCH_SIZE,
                (batchOffset, records) -> publishBatch(request, batchOffset, records, context)
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Unable to process source object s3://%s/%s".formatted(request.sourceBucket(), request.sourceKey()),
                exception
            );
        }
        return null;
    }

    private void validateRequest(ProcessingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Processing request is required");
        }
        if (request.sourceBucket() == null || request.sourceBucket().isBlank()
            || request.sourceKey() == null || request.sourceKey().isBlank()
            || request.objectIdentity() == null || request.objectIdentity().isBlank()) {
            throw new IllegalArgumentException("Processing request must identify the source object");
        }
        if (request.pipelineId() <= 0) {
            throw new IllegalArgumentException("Processing request pipeline ID must be positive");
        }
        if (request.headerEndByte() <= 0
            || request.startByte() < request.headerEndByte()
            || request.endExclusiveByte() <= request.startByte()) {
            throw new IllegalArgumentException("Processing request has an invalid byte range");
        }
        if (request.startLine() < 0
            || request.endExclusiveLine() <= request.startLine()
            || request.endExclusiveLine() - request.startLine() > PROCESSOR_LINE_COUNT
            || request.startLine() % SQS_BATCH_SIZE != 0) {
            throw new IllegalArgumentException("Processing request has an invalid line range");
        }
    }

    private GetObjectRequest rangedObjectRequest(
        ProcessingRequest request,
        long startByte,
        long endExclusiveByte
    ) {
        GetObjectRequest.Builder getObjectRequest = GetObjectRequest.builder()
            .bucket(request.sourceBucket())
            .key(request.sourceKey())
            .range("bytes=%d-%d".formatted(startByte, endExclusiveByte - 1));
        if (request.sourceVersionId() != null && !request.sourceVersionId().isBlank()) {
            getObjectRequest.versionId(request.sourceVersionId());
        }
        if (request.sourceETag() != null && !request.sourceETag().isBlank()) {
            String eTag = request.sourceETag().startsWith("\"")
                ? request.sourceETag()
                : "\"%s\"".formatted(request.sourceETag());
            getObjectRequest.ifMatch(eTag);
        }
        return getObjectRequest.build();
    }

    private void publishBatch(
        ProcessingRequest request,
        int batchOffset,
        List<PaymentRecord> records,
        Context context
    ) {
        int batchIndex = request.startLine() / SQS_BATCH_SIZE + batchOffset;
        List<SqsBatchMessage.Row> rows = new ArrayList<>(records.size());
        long firstLine = (long) request.startLine() + (long) batchOffset * SQS_BATCH_SIZE;
        for (int rowOffset = 0; rowOffset < records.size(); rowOffset++) {
            long dataLineIndex = firstLine + rowOffset;
            rows.add(new SqsBatchMessage.Row(
                stableUuid(request, "record", dataLineIndex),
                records.get(rowOffset).toValues()
            ));
        }

        String batchUuid = stableUuid(request, "batch", batchIndex);
        String body = serialize(new SqsBatchMessage(PaymentRecord.headers(), rows));
        long sqsSentAtMillis = System.currentTimeMillis();
        sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(body)
            .messageAttributes(Map.of(
                "objectIdentity", stringAttribute(request.objectIdentity()),
                "sourceKey", stringAttribute(request.sourceKey()),
                "batchIndex", stringAttribute(Integer.toString(batchIndex)),
                "pipelineId", stringAttribute(Long.toString(request.pipelineId())),
                "batchUuid", stringAttribute(batchUuid),
                "sqsSentAtMs", stringAttribute(Long.toString(sqsSentAtMillis))
            ))
            .build());
        context.getLogger().log(
            "Published SQS batch %d with %d records for pipeline %d.%n"
                .formatted(batchIndex, records.size(), request.pipelineId())
        );
    }

    private String stableUuid(ProcessingRequest request, String itemType, long index) {
        String identity = "%s:%s:%d:%s:%d".formatted(
            request.objectIdentity(),
            request.sourceKey(),
            request.pipelineId(),
            itemType,
            index
        );
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private MessageAttributeValue stringAttribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    private String serialize(SqsBatchMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize SQS batch message", exception);
        }
    }
}
