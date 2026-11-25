package com.passakornsdev.tlq.csv.transform;

import java.util.ArrayList;
import java.util.List;

public record BatchPayload(List<String> headers, List<List<String>> content) {
    public List<List<String>> validatedContent() {
        PaymentRecordSchema.validateHeaders(headers);
        if (content == null) {
            throw new IllegalArgumentException("JSON content must be an array of data rows");
        }

        List<List<String>> rows = new ArrayList<>(content.size());
        for (int index = 0; index < content.size(); index++) {
            List<String> row = content.get(index);
            if (row == null) {
                throw new IllegalArgumentException("JSON content row %d must be an array".formatted(index));
            }
            PaymentRecordSchema.validateRow(row);
            rows.add(row);
        }
        return rows;
    }
}
