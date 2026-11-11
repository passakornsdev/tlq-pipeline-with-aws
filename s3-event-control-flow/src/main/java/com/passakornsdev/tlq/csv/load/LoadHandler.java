package com.passakornsdev.tlq.csv.load;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passakornsdev.tlq.csv.transfer.PaymentRecord;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public class LoadHandler implements RequestHandler<S3Event, Void> {
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final LoadRepository repository;

    public LoadHandler() {
        this(S3Client.create(), new ObjectMapper(), new LoadRepository(DatabaseConfig.fromEnvironment()));
    }

    LoadHandler(S3Client s3Client, ObjectMapper objectMapper, LoadRepository repository) {
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @Override
    public Void handleRequest(S3Event event, Context context) {
        for (S3Event.S3EventNotificationRecord record : event.getRecords()) {
            String bucket = record.getS3().getBucket().getName();
            String objectKey = URLDecoder.decode(
                record.getS3().getObject().getKey(),
                StandardCharsets.UTF_8
            );
            String runId = LoadObjectKey.runId(objectKey);
            List<PaymentRecord> records = readRecords(bucket, objectKey, record.getS3().getObject().getVersionId());
            boolean loaded = repository.loadIfNew(objectKey, runId, records);
            context.getLogger().log(
                "%s %d records from s3://%s/%s with run_id=%s%n"
                    .formatted(loaded ? "Loaded" : "Skipped duplicate", records.size(), bucket, objectKey, runId)
            );
        }
        return null;
    }

    private List<PaymentRecord> readRecords(String bucket, String objectKey, String versionId) {
        GetObjectRequest.Builder request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey);
        if (versionId != null && !versionId.isBlank()) {
            request.versionId(versionId);
        }

        try (ResponseInputStream<GetObjectResponse> input = s3Client.getObject(request.build())) {
            List<Map<String, Object>> values = objectMapper.readValue(input, new TypeReference<>() {});
            return values.stream().map(PaymentRecord::fromJsonMap).toList();
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Unable to read destination object s3://%s/%s".formatted(bucket, objectKey),
                exception
            );
        }
    }
}
