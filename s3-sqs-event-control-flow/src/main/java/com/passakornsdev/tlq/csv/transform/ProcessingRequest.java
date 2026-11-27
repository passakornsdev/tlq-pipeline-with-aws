package com.passakornsdev.tlq.csv.transform;

public record ProcessingRequest(
    String sourceBucket,
    String sourceKey,
    String sourceVersionId,
    String sourceETag,
    String objectIdentity,
    long pipelineId,
    long headerEndByte,
    long startByte,
    long endExclusiveByte,
    int startLine,
    int endExclusiveLine
) {
}
