package com.passakornsdev.tlq.csv.transfer;

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
