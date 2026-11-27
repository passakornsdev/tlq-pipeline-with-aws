package com.passakornsdev.tlq.csv.transform;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class CsvRecordExtractor {
    public BatchPayload extractRecords(
        BufferedReader reader,
        int startLine,
        Integer endExclusiveLine
    ) throws IOException {
        List<String> headers = readHeader(reader);

        int initialCapacity = endExclusiveLine == null ? 10 : endExclusiveLine - startLine;
        List<List<String>> content = new ArrayList<>(initialCapacity);
        int lineNumber = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if (endExclusiveLine != null && lineNumber >= endExclusiveLine) {
                break;
            }
            if (lineNumber >= startLine) {
                content.add(readDataRecord(line, lineNumber));
            }
            lineNumber++;
        }

        if (endExclusiveLine != null && lineNumber < endExclusiveLine) {
            throw new IllegalArgumentException("Source object ended before the assigned line range");
        }
        return new BatchPayload(headers, content);
    }

    public List<String> readHeader(BufferedReader reader) throws IOException {
        String header = reader.readLine();
        if (header == null) {
            throw new IllegalArgumentException("Source object has no CSV header");
        }

        List<String> headers = parseSingleRecord(header, "header");
        PaymentRecordSchema.validateHeaders(headers);
        return headers;
    }

    public int extractBatches(
        BufferedReader reader,
        int startLine,
        int endExclusiveLine,
        int batchSize,
        BiConsumer<Integer, List<List<String>>> batchConsumer
    ) throws IOException {
        if (startLine < 0 || endExclusiveLine <= startLine || batchSize <= 0) {
            throw new IllegalArgumentException("Assigned line range is invalid");
        }

        List<List<String>> content = new ArrayList<>(Math.min(batchSize, endExclusiveLine - startLine));
        int batchOffset = 0;
        for (int lineNumber = startLine; lineNumber < endExclusiveLine; lineNumber++) {
            String line = reader.readLine();
            if (line == null) {
                throw new IllegalArgumentException("Source object ended before the assigned line range");
            }
            content.add(readDataRecord(line, lineNumber));
            if (content.size() == batchSize) {
                batchConsumer.accept(batchOffset++, List.copyOf(content));
                content.clear();
            }
        }
        if (reader.readLine() != null) {
            throw new IllegalArgumentException("Source object exceeded the assigned line range");
        }
        if (!content.isEmpty()) {
            batchConsumer.accept(batchOffset++, List.copyOf(content));
        }
        return batchOffset;
    }

    private List<String> readDataRecord(String line, int lineNumber) {
        List<String> values = parseSingleRecord(line, "data line " + lineNumber);
        PaymentRecordSchema.validateRow(values);
        return values;
    }

    private List<String> parseSingleRecord(String line, String description) {
        try (CSVParser parser = CSVParser.parse(line, CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(false).build())) {
            List<CSVRecord> parsedRecords = parser.getRecords();
            if (parsedRecords.size() != 1) {
                throw new IllegalArgumentException("Expected one CSV record for " + description);
            }
            return parsedRecords.getFirst().toList();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid CSV " + description, exception);
        }
    }
}
