package com.passakornsdev.tlq.csv.load;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passakornsdev.tlq.csv.database.DatabaseConfig;
import com.passakornsdev.tlq.csv.database.PipelineInfoRepository;
import com.passakornsdev.tlq.csv.database.SaafInvocationRecorder;
import com.passakornsdev.tlq.csv.saaf.Inspector;
import com.passakornsdev.tlq.csv.transform.BatchPayload;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import static com.passakornsdev.tlq.csv.util.CommonValidator.requiredEnvironmentVariable;

public class LoadHandler implements RequestHandler<S3Event, Void> {
    private final S3Client s3Client;
    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;
    private final LoadRepository repository;
    private final PipelineInfoRepository pipelineInfoRepository;
    private final SaafInvocationRecorder invocationRecorder;
    private final String queryFunctionName;

    public LoadHandler() {
        this(
            S3Client.create(),
            LambdaClient.create(),
            new ObjectMapper(),
            DatabaseConfig.fromEnvironment(),
            requiredEnvironmentVariable("QUERY_FUNCTION_NAME")
        );
    }

    private LoadHandler(
        S3Client s3Client,
        LambdaClient lambdaClient,
        ObjectMapper objectMapper,
        DatabaseConfig databaseConfig,
        String queryFunctionName
    ) {
        this(
            s3Client,
            lambdaClient,
            objectMapper,
            new LoadRepository(databaseConfig),
            new PipelineInfoRepository(databaseConfig),
            new SaafInvocationRecorder(databaseConfig),
            queryFunctionName
        );
    }

    LoadHandler(
        S3Client s3Client,
        LambdaClient lambdaClient,
        ObjectMapper objectMapper,
        LoadRepository repository,
        PipelineInfoRepository pipelineInfoRepository,
        SaafInvocationRecorder invocationRecorder,
        String queryFunctionName
    ) {
        this.s3Client = s3Client;
        this.lambdaClient = lambdaClient;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.pipelineInfoRepository = pipelineInfoRepository;
        this.invocationRecorder = invocationRecorder;
        this.queryFunctionName = queryFunctionName;
    }

    @Override
    public Void handleRequest(S3Event event, Context context) {
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

    private Void processRequest(S3Event event, Context context, Inspector inspector) {
        inspector.inspectContainer();
        recordS3Latency(event, context, inspector);

        for (S3Event.S3EventNotificationRecord record : event.getRecords()) {
            String bucket = record.getS3().getBucket().getName();
            String objectKey = URLDecoder.decode(
                record.getS3().getObject().getKey(),
                StandardCharsets.UTF_8
            );
            String runId = LoadObjectKey.runId(objectKey);
            List<List<String>> content = readRecords(bucket, objectKey, record.getS3().getObject().getVersionId());
            boolean loaded = repository.loadIfNew(objectKey, runId, content);
            boolean queryTriggered = loaded && pipelineInfoRepository.markLatestFinishedAndShouldQuery();
            if (queryTriggered) {
                invokeQuery();
            }
            context.getLogger().log(
                "%s %d records from s3://%s/%s with run_id=%s%s%n"
                    .formatted(
                        loaded ? "Loaded" : "Skipped duplicate",
                        content.size(),
                        bucket,
                        objectKey,
                        runId,
                        queryTriggered ? "; triggered aggregate query" : ""
                    )
            );
        }
        return null;
    }

    private void recordS3Latency(S3Event event, Context context, Inspector inspector) {
        if (event.getRecords() == null || event.getRecords().size() != 1) {
            inspector.addAttribute("s3LatencyMs", null);
            context.getLogger().log(
                "Expected one S3 object record per loader invocation; s3 latency will be stored as NULL.%n"
                    .formatted()
            );
            return;
        }

        S3Event.S3EventNotificationRecord record = event.getRecords().get(0);
        if (record.getEventTime() == null) {
            inspector.addAttribute("s3LatencyMs", null);
            context.getLogger().log(
                "S3 event %s has no event time; latency will be stored as NULL.%n"
                    .formatted(context.getAwsRequestId())
            );
            return;
        }

        Object startTimeMetric = inspector.getAttribute("startTime");
        if (!(startTimeMetric instanceof Number startTime)) {
            throw new IllegalStateException("SAAF startTime is unavailable");
        }
        inspector.addAttribute("s3LatencyMs", startTime.longValue() - record.getEventTime().getMillis());
    }

    private List<List<String>> readRecords(String bucket, String objectKey, String versionId) {
        GetObjectRequest.Builder request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey);
        if (versionId != null && !versionId.isBlank()) {
            request.versionId(versionId);
        }

        try (ResponseInputStream<GetObjectResponse> input = s3Client.getObject(request.build())) {
            JsonNode payload = objectMapper.readTree(input);
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("Destination JSON must be an object");
            }
            List<String> headers = readStringArray(payload.get("headers"), "headers");
            JsonNode contentNode = payload.get("content");
            if (contentNode == null || !contentNode.isArray()) {
                throw new IllegalArgumentException("JSON content must be an array of data rows");
            }
            List<List<String>> content = new ArrayList<>(contentNode.size());
            for (int index = 0; index < contentNode.size(); index++) {
                content.add(readStringArray(contentNode.get(index), "content row %d".formatted(index)));
            }
            BatchPayload batch = new BatchPayload(headers, content);
            return batch.validatedContent();
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Unable to read destination object s3://%s/%s".formatted(bucket, objectKey),
                exception
            );
        }
    }

    private List<String> readStringArray(JsonNode node, String description) {
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException("JSON %s must be an array".formatted(description));
        }
        List<String> values = new ArrayList<>(node.size());
        for (int index = 0; index < node.size(); index++) {
            JsonNode value = node.get(index);
            if (!value.isTextual()) {
                throw new IllegalArgumentException(
                    "JSON %s value %d must be a string".formatted(description, index)
                );
            }
            values.add(value.textValue());
        }
        return values;
    }

    private void invokeQuery() {
        lambdaClient.invoke(InvokeRequest.builder()
            .functionName(queryFunctionName)
            .invocationType(InvocationType.EVENT)
            .payload(SdkBytes.fromUtf8String("{}"))
            .build());
    }
}
