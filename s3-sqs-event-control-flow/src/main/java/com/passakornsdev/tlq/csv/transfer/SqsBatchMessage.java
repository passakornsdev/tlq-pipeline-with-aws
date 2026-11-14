package com.passakornsdev.tlq.csv.transfer;

import java.util.List;

public record SqsBatchMessage(
    List<String> header,
    List<List<String>> rows
) {
}
