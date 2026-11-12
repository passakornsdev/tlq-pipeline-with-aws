package com.passakornsdev.tlq.csv.transfer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passakornsdev.tlq.csv.database.DatabaseConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.core.ResponseInputStream;

public class DispatcherHandler implements RequestHandler<S3Event, Void> {
    private static final int BATCH_SIZE = 10_000;

    private final S3Client s3Client;
    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;
    private final String processorFunctionName;
    private final String destinationBucket;
    private final DatabaseConfig databaseConfig;

    public DispatcherHandler() {
        this(
            S3Client.create(),
            LambdaClient.create(),
            new ObjectMapper(),
            requiredEnvironmentVariable("PROCESSOR_FUNCTION_NAME"),
            requiredEnvironmentVariable("DESTINATION_BUCKET"),
            DatabaseConfig.fromEnvironment()
        );
    }

    DispatcherHandler(
        S3Client s3Client,
        LambdaClient lambdaClient,
        ObjectMapper objectMapper,
        String processorFunctionName,
        String destinationBucket,
        DatabaseConfig databaseConfig
    ) {
        this.s3Client = s3Client;
        this.lambdaClient = lambdaClient;
        this.objectMapper = objectMapper;
        this.processorFunctionName = processorFunctionName;
        this.destinationBucket = destinationBucket;
        this.databaseConfig = databaseConfig;
    }

    @Override
    public Void handleRequest(S3Event event, Context context) {
        dropAllTables();
        context.getLogger().log("Dropped all tables from the configured database.%n");
        for (S3Event.S3EventNotificationRecord record : event.getRecords()) {
            String bucket = record.getS3().getBucket().getName();
            String key = URLDecoder.decode(record.getS3().getObject().getKey(), StandardCharsets.UTF_8);
            String versionId = record.getS3().getObject().getVersionId();
            String objectIdentity = versionId == null || versionId.isBlank()
                ? record.getS3().getObject().getSequencer()
                : versionId;

            int dataLineCount = countDataLines(bucket, key, versionId);
            int batchCount = dispatchBatches(bucket, key, versionId, objectIdentity, dataLineCount);
            context.getLogger().log(
                "Dispatched %d batches for s3://%s/%s containing %d data lines.%n"
                    .formatted(batchCount, bucket, key, dataLineCount)
            );
        }
        return null;
    }

    private void dropAllTables() {
        try (Connection connection = databaseConfig.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS loaded_destination_objects");
            statement.executeUpdate("DROP TABLE IF EXISTS payments_raw");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to drop tables from the configured database", exception);
        }
    }

    private int countDataLines(String bucket, String key, String versionId) {
        GetObjectRequest.Builder request = GetObjectRequest.builder().bucket(bucket).key(key);
        if (versionId != null && !versionId.isBlank()) {
            request.versionId(versionId);
        }

        try (
            ResponseInputStream<GetObjectResponse> input = s3Client.getObject(request.build());
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
        ) {
            if (reader.readLine() == null) {
                return 0;
            }

            int count = 0;
            while (reader.readLine() != null) {
                count++;
            }
            return count;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read source object s3://%s/%s".formatted(bucket, key), exception);
        }
    }

    private int dispatchBatches(
        String sourceBucket,
        String sourceKey,
        String sourceVersionId,
        String objectIdentity,
        int dataLineCount
    ) {
        int batchIndex = 0;
        for (int startLine = 0; startLine < dataLineCount; startLine += BATCH_SIZE) {
            int endExclusiveLine = Math.min(startLine + BATCH_SIZE, dataLineCount);
            ProcessingRequest request = new ProcessingRequest(
                sourceBucket,
                sourceKey,
                sourceVersionId,
                destinationBucket,
                Objects.requireNonNull(objectIdentity, "S3 event must include a version ID or sequencer"),
                startLine,
                endExclusiveLine,
                batchIndex
            );

            lambdaClient.invoke(InvokeRequest.builder()
                .functionName(processorFunctionName)
                .invocationType(InvocationType.EVENT)
                .payload(SdkBytes.fromUtf8String(serialize(request)))
                .build());
            batchIndex++;
        }
        return batchIndex;
    }

    private String serialize(ProcessingRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize processing request", exception);
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
