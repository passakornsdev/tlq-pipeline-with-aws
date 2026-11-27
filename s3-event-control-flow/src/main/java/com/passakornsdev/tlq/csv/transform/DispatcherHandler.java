package com.passakornsdev.tlq.csv.transform;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passakornsdev.tlq.csv.database.DatabaseConfig;
import com.passakornsdev.tlq.csv.database.PipelineInfoRepository;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.passakornsdev.tlq.csv.saaf.Inspector;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import static com.passakornsdev.tlq.csv.util.CommonValidator.requiredEnvironmentVariable;

public class DispatcherHandler implements RequestHandler<S3Event, Void> {
    private static final int PROCESSOR_LINE_COUNT = 100_000;
    private static final int OUTPUT_BATCH_SIZE = 1_000;

    private final S3Client s3Client;
    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;
    private final String processorFunctionName;
    private final String destinationBucket;
    private final DatabaseConfig databaseConfig;
    private final PipelineInfoRepository pipelineInfoRepository;

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
        this.pipelineInfoRepository = new PipelineInfoRepository(databaseConfig);
    }

    @Override
    public Void handleRequest(S3Event event, Context context) {
        //Collect inital data.
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

            SourceFileInfo sourceFileInfo = inspectSourceObject(bucket, key, versionId);
            int dataLineCount = sourceFileInfo.dataLineCount();
            int batchCount = batchCount(dataLineCount);
            pipelineInfoRepository.createPipeline(batchCount);
            int dispatchedCount = dispatchProcessors(
                bucket,
                key,
                versionId,
                objectIdentity,
                sourceFileInfo
            );
            context.getLogger().log(
                "Dispatched %d processors for s3://%s/%s containing %d data lines.%n"
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
            statement.executeUpdate("DROP TABLE IF EXISTS pipeline_info");
            statement.executeUpdate("DROP TABLE IF EXISTS loaded_destination_objects");
            statement.executeUpdate("DROP TABLE IF EXISTS payments_raw");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to drop tables from the configured database", exception);
        }
    }

    private SourceFileInfo inspectSourceObject(String bucket, String key, String versionId) {
        GetObjectRequest.Builder request = GetObjectRequest.builder().bucket(bucket).key(key);
        if (versionId != null && !versionId.isBlank()) {
            request.versionId(versionId);
        }

        try (
            ResponseInputStream<GetObjectResponse> objectInput = s3Client.getObject(request.build());
            PushbackInputStream input = new PushbackInputStream(new BufferedInputStream(objectInput), 1)
        ) {
            SourceFileScan scan = new SourceFileScan();
            long byteOffset = 0;
            long lineStartByte = 0;
            int value;
            while ((value = input.read()) != -1) {
                byteOffset++;
                if (value != '\r' && value != '\n') {
                    continue;
                }
                if (value == '\r') {
                    int next = input.read();
                    if (next == '\n') {
                        byteOffset++;
                    } else if (next != -1) {
                        input.unread(next);
                    }
                }
                scan.recordLineEnd(byteOffset);
                lineStartByte = byteOffset;
            }
            if (lineStartByte < byteOffset) {
                scan.recordLineEnd(byteOffset);
            }
            return scan.finish(byteOffset, objectInput.response().eTag());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read source object s3://%s/%s".formatted(bucket, key), exception);
        }
    }

    private int dispatchProcessors(
        String sourceBucket,
        String sourceKey,
        String sourceVersionId,
        String objectIdentity,
        SourceFileInfo sourceFileInfo
    ) {
        int processorCount = 0;
        for (ProcessorRange range : sourceFileInfo.processorRanges()) {
            ProcessingRequest request = new ProcessingRequest(
                sourceBucket,
                sourceKey,
                sourceVersionId,
                destinationBucket,
                sourceFileInfo.sourceETag(),
                Objects.requireNonNull(objectIdentity, "S3 event must include a version ID or sequencer"),
                sourceFileInfo.headerEndByte(),
                range.startByte(),
                range.endExclusiveByte(),
                range.startLine(),
                range.endExclusiveLine()
            );

            lambdaClient.invoke(InvokeRequest.builder()
                .functionName(processorFunctionName)
                .invocationType(InvocationType.EVENT)
                .payload(SdkBytes.fromUtf8String(serialize(request)))
                .build());
            processorCount++;
        }
        return processorCount;
    }

    private int batchCount(int dataLineCount) {
        return dataLineCount / OUTPUT_BATCH_SIZE
            + (dataLineCount % OUTPUT_BATCH_SIZE == 0 ? 0 : 1);
    }

    private String serialize(ProcessingRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize processing request", exception);
        }
    }

    private record SourceFileInfo(
        long headerEndByte,
        int dataLineCount,
        String sourceETag,
        List<ProcessorRange> processorRanges
    ) {
    }

    private record ProcessorRange(int startLine, int endExclusiveLine, long startByte, long endExclusiveByte) {
    }

    private static class SourceFileScan {
        private final List<ProcessorRange> ranges = new ArrayList<>();
        private long headerEndByte = -1;
        private long rangeStartByte;
        private int dataLineCount;
        private int rangeStartLine;

        private void recordLineEnd(long endExclusiveByte) {
            if (headerEndByte < 0) {
                headerEndByte = endExclusiveByte;
                rangeStartByte = endExclusiveByte;
                return;
            }

            dataLineCount++;
            if (dataLineCount - rangeStartLine == PROCESSOR_LINE_COUNT) {
                ranges.add(new ProcessorRange(
                    rangeStartLine,
                    dataLineCount,
                    rangeStartByte,
                    endExclusiveByte
                ));
                rangeStartLine = dataLineCount;
                rangeStartByte = endExclusiveByte;
            }
        }

        private SourceFileInfo finish(long objectEndByte, String sourceETag) {
            if (headerEndByte < 0) {
                headerEndByte = objectEndByte;
            }
            if (dataLineCount > rangeStartLine) {
                ranges.add(new ProcessorRange(
                    rangeStartLine,
                    dataLineCount,
                    rangeStartByte,
                    objectEndByte
                ));
            }
            return new SourceFileInfo(headerEndByte, dataLineCount, sourceETag, List.copyOf(ranges));
        }
    }

}
