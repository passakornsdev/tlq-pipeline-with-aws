package com.passakornsdev.tlq.csv.transfer;


import java.io.BufferedReader;
import java.io.FileReader;

public class Test {
    public static void main(String[] args) {
        try (BufferedReader reader = new BufferedReader(new FileReader("1K_RECORDS.csv"))) {
            var res = new CsvRecordExtractor().extractRecords(reader, 0, null);
            System.out.println(res);
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}
