package com.passakornsdev.tlq.csv.transfer;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class CsvRecordExtractor {
    public List<PaymentRecord> extractRecords(
        BufferedReader reader,
        int startLine,
        Integer endExclusiveLine
    ) throws IOException {
        String header = reader.readLine();
        if (header == null) {
            throw new IllegalArgumentException("Source object has no CSV header");
        }

        PaymentRecord.validateHeaders(parseSingleRecord(header, "header"));

        int initialCapacity = endExclusiveLine == null ? 10 : endExclusiveLine - startLine;
        List<PaymentRecord> records = new ArrayList<>(initialCapacity);
        int lineNumber = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if (endExclusiveLine != null && lineNumber >= endExclusiveLine) {
                break;
            }
            if (lineNumber >= startLine) {
                records.add(PaymentRecord.fromValues(parseSingleRecord(line, "data line " + lineNumber)));
            }
            lineNumber++;
        }

        if (endExclusiveLine != null && lineNumber < endExclusiveLine) {
            throw new IllegalArgumentException("Source object ended before the assigned line range");
        }
        return records;
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
