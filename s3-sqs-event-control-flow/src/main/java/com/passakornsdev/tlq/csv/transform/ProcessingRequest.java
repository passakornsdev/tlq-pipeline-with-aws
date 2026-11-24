package com.passakornsdev.tlq.csv.transform;

public record ProcessingRequest(
    String sourceBucket,
    String sourceKey,
    String sourceVersionId,
    String objectIdentity,
    int startLine,
    int endExclusiveLine,
    int batchIndex
) {
}
