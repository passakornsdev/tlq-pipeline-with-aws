package com.passakornsdev.tlq.csv.transfer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
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
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final CsvRecordExtractor csvRecordExtractor;

    public ProcessorHandler() {
        this(S3Client.create(), new ObjectMapper(), new CsvRecordExtractor());
    }

    ProcessorHandler(S3Client s3Client, ObjectMapper objectMapper) {
        this(s3Client, objectMapper, new CsvRecordExtractor());
    }

    ProcessorHandler(
        S3Client s3Client,
        ObjectMapper objectMapper,
        CsvRecordExtractor csvRecordExtractor
    ) {
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.csvRecordExtractor = csvRecordExtractor;
    }

    @Override
    public Void handleRequest(ProcessingRequest request, Context context) {
        List<PaymentRecord> records = readBatch(request);
        String outputKey = outputKey(request);

        try {
            byte[] body = objectMapper.writeValueAsBytes(records);
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(request.destinationBucket())
                    .key(outputKey)
                    .contentType("application/json")
                    .build(),
                RequestBody.fromBytes(body)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize batch JSON", exception);
        }

        context.getLogger().log(
            "Wrote %d records from s3://%s/%s to s3://%s/%s%n"
                .formatted(records.size(), request.sourceBucket(), request.sourceKey(),
                    request.destinationBucket(), outputKey)
        );
        return null;
    }

    private List<PaymentRecord> readBatch(ProcessingRequest request) {
        GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
            .bucket(request.sourceBucket())
            .key(request.sourceKey());
        if (request.sourceVersionId() != null && !request.sourceVersionId().isBlank()) {
            requestBuilder.versionId(request.sourceVersionId());
        }

        try (
            ResponseInputStream<GetObjectResponse> input = s3Client.getObject(requestBuilder.build());
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
        ) {
            return csvRecordExtractor.extractRecords(reader, request.startLine(), request.endExclusiveLine());
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Unable to read source object s3://%s/%s".formatted(request.sourceBucket(), request.sourceKey()),
                exception
            );
        }
    }

    private String outputKey(ProcessingRequest request) {
        return "converted/%s/%s/batch-%06d.json".formatted(
            request.objectIdentity(),
            request.sourceKey(),
            request.batchIndex()
        );
    }
}
