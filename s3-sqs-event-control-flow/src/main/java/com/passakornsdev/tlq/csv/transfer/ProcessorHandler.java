package com.passakornsdev.tlq.csv.transfer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import static com.passakornsdev.tlq.csv.util.CommonValidator.requiredEnvironmentVariable;

public class ProcessorHandler implements RequestHandler<ProcessingRequest, Void> {
    private final S3Client s3Client;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final CsvRecordExtractor csvRecordExtractor;
    private final String queueUrl;

    public ProcessorHandler() {
        this(
            S3Client.create(),
            SqsClient.create(),
            new ObjectMapper(),
            new CsvRecordExtractor(),
            requiredEnvironmentVariable("SQS_QUEUE_URL")
        );
    }

    ProcessorHandler(
        S3Client s3Client,
        SqsClient sqsClient,
        ObjectMapper objectMapper,
        CsvRecordExtractor csvRecordExtractor,
        String queueUrl
    ) {
        this.s3Client = s3Client;
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.csvRecordExtractor = csvRecordExtractor;
        this.queueUrl = queueUrl;
    }

    @Override
    public Void handleRequest(ProcessingRequest request, Context context) {
        List<PaymentRecord> records = readBatch(request);

        try {
            String body = objectMapper.writeValueAsString(
                new SqsBatchMessage(PaymentRecord.headers(), records.stream().map(PaymentRecord::toValues).toList())
            );
            sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .messageAttributes(Map.of(
                    "objectIdentity", stringAttribute(request.objectIdentity()),
                    "sourceKey", stringAttribute(request.sourceKey()),
                    "batchIndex", stringAttribute(Integer.toString(request.batchIndex()))
                ))
                .build());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize batch SQS message", exception);
        }

        context.getLogger().log(
            "Sent %d records from s3://%s/%s to SQS queue %s%n"
                .formatted(records.size(), request.sourceBucket(), request.sourceKey(),
                    queueUrl)
        );
        return null;
    }

    private MessageAttributeValue stringAttribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
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

}
