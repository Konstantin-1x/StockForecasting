package org.example.web.data;

import java.nio.file.Path;

public record DataExportFile(
        String fileName,
        Path path,
        long size
) {
}
