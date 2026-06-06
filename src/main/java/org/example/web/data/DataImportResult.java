package org.example.web.data;

import java.util.Map;

public record DataImportResult(
        long totalRows,
        Map<String, Long> tableRows
) {
}
