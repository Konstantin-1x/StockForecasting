package org.example.parser.wb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class CookieFileLoader {

    private static final Logger log = LoggerFactory.getLogger(CookieFileLoader.class);

    private CookieFileLoader() {
    }

    static CookieRotator load(Path cookieFile) {
        if (cookieFile == null || !Files.exists(cookieFile)) {
            log.warn("Cookie file was not found: {}", cookieFile);
            return new CookieRotator(List.of(""));
        }

        List<String> headers = new ArrayList<>();
        List<String> cookiePairs = new ArrayList<>();
        try {
            for (String rawLine : Files.readAllLines(cookieFile)) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.regionMatches(true, 0, "Cookie:", 0, "Cookie:".length())) {
                    line = line.substring("Cookie:".length()).trim();
                }
                if (line.contains("=")) {
                    if (line.contains(";")) {
                        headers.add(normalizeHeader(line));
                    } else {
                        cookiePairs.add(line);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read cookie file: {}", cookieFile, e);
            return new CookieRotator(List.of(""));
        }

        if (!cookiePairs.isEmpty()) {
            headers.add(normalizeHeader(String.join("; ", cookiePairs)));
        }

        log.info("Loaded {} cookie set(s) for Wildberries parser", headers.size());
        return new CookieRotator(headers);
    }

    private static String normalizeHeader(String header) {
        return header
                .replace("\r", "")
                .replace("\n", "; ")
                .replaceAll(";\\s*;", ";")
                .trim();
    }
}
