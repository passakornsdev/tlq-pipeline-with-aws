package com.passakornsdev.tlq.csv.transform;

import java.util.List;

public record SqsBatchMessage(
    List<String> header,
    List<Row> rows
) {
    public record Row(
        String recordUuid,
        List<String> values
    ) {
    }
}
