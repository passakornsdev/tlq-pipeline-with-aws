package com.passakornsdev.tlq.csv.transform;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.passakornsdev.tlq.csv.database.DatabaseConfig;
import com.passakornsdev.tlq.csv.database.SaafInvocationRecorder;
import com.passakornsdev.tlq.csv.saaf.Inspector;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class ProcessorHandler implements RequestHandler<ProcessingRequest, Void> {
    private static final int PROCESSOR_LINE_COUNT = 100_000;
    private static final int OUTPUT_BATCH_SIZE = 1_000;

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final CsvRecordExtractor csvRecordExtractor;
    private final SaafInvocationRecorder invocationRecorder;

    public ProcessorHandler() {
        this(
            S3Client.create(),
            new ObjectMapper(),
            new CsvRecordExtractor(),
            new SaafInvocationRecorder(DatabaseConfig.fromEnvironment())
        );
    }

    ProcessorHandler(
        S3Client s3Client,
        ObjectMapper objectMapper,
        CsvRecordExtractor csvRecordExtractor,
        SaafInvocationRecorder invocationRecorder
    ) {
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.csvRecordExtractor = csvRecordExtractor;
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
        inspector.inspectContainer();

        validateRequest(request);
        int outputObjectCount = writeBatches(request);

        context.getLogger().log(
            "Wrote %d S3 batch objects for data lines [%d, %d) from s3://%s/%s.%n"
                .formatted(
                    outputObjectCount,
                    request.startLine(),
                    request.endExclusiveLine(),
                    request.sourceBucket(),
                    request.sourceKey()
                )
        );
        return null;
    }

    private int writeBatches(ProcessingRequest request) {
        List<String> headers;
        try (
            ResponseInputStream<GetObjectResponse> headerInput = s3Client.getObject(
                rangedObjectRequest(request, 0, request.headerEndByte())
            );
            BufferedReader headerReader = new BufferedReader(
                new InputStreamReader(headerInput, StandardCharsets.UTF_8)
            )
        ) {
            headers = csvRecordExtractor.readHeader(headerReader);
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
            return csvRecordExtractor.extractBatches(
                reader,
                request.startLine(),
                request.endExclusiveLine(),
                OUTPUT_BATCH_SIZE,
                (batchOffset, records) -> writeBatch(
                    request,
                    headers,
                    records,
                    request.startLine() / OUTPUT_BATCH_SIZE + batchOffset
                )
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Unable to read source object s3://%s/%s".formatted(request.sourceBucket(), request.sourceKey()),
                exception
            );
        }
    }

    private void writeBatch(
        ProcessingRequest request,
        List<String> headers,
        List<List<String>> records,
        int batchIndex
    ) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(new BatchPayload(headers, records));
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(request.destinationBucket())
                    .key(outputKey(request, batchIndex))
                    .contentType("application/json")
                    .build(),
                RequestBody.fromBytes(body)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize batch JSON", exception);
        }
    }

    private void validateRequest(ProcessingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Processing request is required");
        }
        if (request.sourceBucket() == null || request.sourceBucket().isBlank()
            || request.sourceKey() == null || request.sourceKey().isBlank()
            || request.destinationBucket() == null || request.destinationBucket().isBlank()
            || request.objectIdentity() == null || request.objectIdentity().isBlank()) {
            throw new IllegalArgumentException("Processing request must identify the source and destination objects");
        }
        if (request.headerEndByte() <= 0
            || request.startByte() < request.headerEndByte()
            || request.endExclusiveByte() <= request.startByte()) {
            throw new IllegalArgumentException("Processing request has an invalid byte range");
        }
        if (request.startLine() < 0
            || request.endExclusiveLine() <= request.startLine()
            || request.endExclusiveLine() - request.startLine() > PROCESSOR_LINE_COUNT
            || request.startLine() % PROCESSOR_LINE_COUNT != 0) {
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

    private String outputKey(ProcessingRequest request, int batchIndex) {
        return "converted/%s/%s/batch-%06d.json".formatted(
            request.objectIdentity(),
            request.sourceKey(),
            batchIndex
        );
    }
}
