package com.passakornsdev.tlq.csv.load;

public final class LoadObjectKey {
    private static final String PREFIX = "converted/";
    private static final String BATCH_MARKER = "/batch-";

    private LoadObjectKey() {
    }

    public static String runId(String objectKey) {
        if (objectKey == null || !objectKey.startsWith(PREFIX) || !objectKey.endsWith(".json")) {
            throw new IllegalArgumentException("Unexpected destination object key: " + objectKey);
        }

        int batchMarker = objectKey.lastIndexOf(BATCH_MARKER);
        if (batchMarker <= PREFIX.length() || batchMarker + BATCH_MARKER.length() >= objectKey.length() - 5) {
            throw new IllegalArgumentException("Destination object is missing a batch suffix: " + objectKey);
        }

        String batchNumber = objectKey.substring(batchMarker + BATCH_MARKER.length(), objectKey.length() - 5);
        if (!batchNumber.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Destination object has an invalid batch suffix: " + objectKey);
        }
        return objectKey.substring(0, batchMarker);
    }
}
