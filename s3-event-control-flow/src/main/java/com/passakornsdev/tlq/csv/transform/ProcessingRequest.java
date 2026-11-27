package com.passakornsdev.tlq.csv.transform;

public record ProcessingRequest(
    String sourceBucket,
    String sourceKey,
    String sourceVersionId,
    String destinationBucket,
    String sourceETag,
    String objectIdentity,
    long headerEndByte,
    long startByte,
    long endExclusiveByte,
    int startLine,
    int endExclusiveLine
) {
}
