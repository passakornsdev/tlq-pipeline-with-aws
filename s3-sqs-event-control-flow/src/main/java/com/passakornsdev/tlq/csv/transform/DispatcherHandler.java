package com.passakornsdev.tlq.csv.transform;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passakornsdev.tlq.csv.database.DatabaseConfig;
import com.passakornsdev.tlq.csv.database.PipelineInfoRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.passakornsdev.tlq.csv.saaf.Inspector;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import static com.passakornsdev.tlq.csv.util.CommonValidator.requiredEnvironmentVariable;

public class DispatcherHandler implements RequestHandler<S3Event, Void> {
    private static final int BATCH_SIZE = 1000;
    private static final int BATCHES_PER_DISPATCH_CYCLE = 50;
    private static final long DISPATCH_CYCLE_INTERVAL_MILLIS = 1_000L;

    private final S3Client s3Client;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final CsvRecordExtractor csvRecordExtractor;
    private final String queueUrl;
    private final DatabaseConfig databaseConfig;
    private final PipelineInfoRepository pipelineInfoRepository;

    public DispatcherHandler() {
        this(
            S3Client.create(),
            SqsClient.create(),
            new ObjectMapper(),
            new CsvRecordExtractor(),
            requiredEnvironmentVariable("SQS_QUEUE_URL"),
            DatabaseConfig.fromEnvironment()
        );
    }

    DispatcherHandler(
        S3Client s3Client,
        SqsClient sqsClient,
        ObjectMapper objectMapper,
        CsvRecordExtractor csvRecordExtractor,
        String queueUrl,
        DatabaseConfig databaseConfig
    ) {
        this.s3Client = s3Client;
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.csvRecordExtractor = csvRecordExtractor;
        this.queueUrl = queueUrl;
        this.databaseConfig = databaseConfig;
        this.pipelineInfoRepository = new PipelineInfoRepository(databaseConfig);
    }

    @Override
    public Void handleRequest(S3Event event, Context context) {
        Inspector inspector = new Inspector();
        inspector.inspectAll();
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
            int batchCount = batchCount(dataLineCount);
            long pipelineId = pipelineInfoRepository.createPipeline(batchCount);
            int dispatchedCount = dispatchBatches(
                bucket,
                key,
                versionId,
                objectIdentity,
                pipelineId,
                dataLineCount
            );
            context.getLogger().log(
                "Dispatched %d batches for s3://%s/%s containing %d data lines.%n"
                    .formatted(dispatchedCount, bucket, key, dataLineCount)
            );
        }
        inspector.inspectAllDeltas();
        context.getLogger().log(inspector.finish().toString());
        return null;
    }

    private void dropAllTables() {
        try (Connection connection = databaseConfig.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS successful_batches");
            statement.executeUpdate("DROP TABLE IF EXISTS pipeline_info");
            statement.executeUpdate("DROP TABLE IF EXISTS loaded_sqs_messages");
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
        long pipelineId,
        int dataLineCount
    ) {
        int batchIndex = 0;
        int startLine = 0;
        while (startLine < dataLineCount) {
            for (int batchesInCycle = 0;
                 batchesInCycle < BATCHES_PER_DISPATCH_CYCLE && startLine < dataLineCount;
                 batchesInCycle++) {
                int endExclusiveLine = Math.min(startLine + BATCH_SIZE, dataLineCount);
                publishBatch(
                    sourceBucket,
                    sourceKey,
                    sourceVersionId,
                    Objects.requireNonNull(objectIdentity, "S3 event must include a version ID or sequencer"),
                    pipelineId,
                    startLine,
                    endExclusiveLine,
                    batchIndex
                );
                startLine = endExclusiveLine;
                batchIndex++;
            }
            if (startLine < dataLineCount) {
                try {
                    Thread.sleep(DISPATCH_CYCLE_INTERVAL_MILLIS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting between batch dispatch cycles", exception);
                }
            }
        }
        return batchIndex;
    }

    private void publishBatch(
        String sourceBucket,
        String sourceKey,
        String sourceVersionId,
        String objectIdentity,
        long pipelineId,
        int startLine,
        int endExclusiveLine,
        int batchIndex
    ) {
        List<PaymentRecord> records = readBatch(
            sourceBucket,
            sourceKey,
            sourceVersionId,
            startLine,
            endExclusiveLine
        );
        List<SqsBatchMessage.Row> rows = new ArrayList<>(records.size());
        for (PaymentRecord record : records) {
            rows.add(new SqsBatchMessage.Row(UUID.randomUUID().toString(), record.toValues()));
        }

        String batchUuid = UUID.randomUUID().toString();
        String body = serialize(new SqsBatchMessage(PaymentRecord.headers(), rows));
        sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(body)
            .messageAttributes(Map.of(
                "objectIdentity", stringAttribute(objectIdentity),
                "sourceKey", stringAttribute(sourceKey),
                "batchIndex", stringAttribute(Integer.toString(batchIndex)),
                "pipelineId", stringAttribute(Long.toString(pipelineId)),
                "batchUuid", stringAttribute(batchUuid)
            ))
            .build());
    }

    private List<PaymentRecord> readBatch(
        String bucket,
        String key,
        String versionId,
        int startLine,
        int endExclusiveLine
    ) {
        GetObjectRequest.Builder request = GetObjectRequest.builder().bucket(bucket).key(key);
        if (versionId != null && !versionId.isBlank()) {
            request.versionId(versionId);
        }

        try (
            ResponseInputStream<GetObjectResponse> input = s3Client.getObject(request.build());
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
        ) {
            return csvRecordExtractor.extractRecords(reader, startLine, endExclusiveLine);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read source object s3://%s/%s".formatted(bucket, key), exception);
        }
    }

    private MessageAttributeValue stringAttribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    private int batchCount(int dataLineCount) {
        return dataLineCount / BATCH_SIZE + (dataLineCount % BATCH_SIZE == 0 ? 0 : 1);
    }

    private String serialize(SqsBatchMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize batch SQS message", exception);
        }
    }

}
