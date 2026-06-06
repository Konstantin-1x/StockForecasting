package org.example.parser.wb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class CookieFileLoader {

    private static final Logger log = LoggerFactory.getLogger(CookieFileLoader.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private CookieFileLoader() {
    }

    static CookieRotator load(Path cookieFile, int switchWindowSize, int switchMinSamples, double switchThreshold) {
        if (cookieFile == null || !Files.exists(cookieFile)) {
            log.warn("Cookie file was not found: {}", cookieFile);
            return new CookieRotator(List.of(""), switchWindowSize, switchMinSamples, switchThreshold);
        }

        List<String> headers = new ArrayList<>();
        List<String> cookiePairs = new ArrayList<>();
        try {
            String content = Files.readString(cookieFile);
            if (content.trim().startsWith("[")) {
                headers.addAll(parseJsonCookieSets(content));
            } else {
                for (String rawLine : content.lines().toList()) {
                    String line = rawLine == null ? "" : rawLine.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    boolean cookieHeaderLine = line.regionMatches(true, 0, "Cookie:", 0, "Cookie:".length());
                    if (line.regionMatches(true, 0, "Cookie:", 0, "Cookie:".length())) {
                        line = line.substring("Cookie:".length()).trim();
                    }
                    if (line.contains("=")) {
                        if (cookieHeaderLine || line.contains(";")) {
                            headers.add(normalizeHeader(line));
                        } else {
                            cookiePairs.add(line);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read cookie file: {}", cookieFile, e);
            return new CookieRotator(List.of(""), switchWindowSize, switchMinSamples, switchThreshold);
        }

        if (!cookiePairs.isEmpty()) {
            headers.add(normalizeHeader(String.join("; ", cookiePairs)));
        }

        log.info("Loaded {} cookie set(s) for Wildberries parser", headers.size());
        return new CookieRotator(headers, switchWindowSize, switchMinSamples, switchThreshold);
    }

    private static List<String> parseJsonCookieSets(String content) throws IOException {
        List<Map<String, Object>> cookieSets = objectMapper.readValue(content, new TypeReference<>() {
        });
        List<String> headers = new ArrayList<>();
        for (Map<String, Object> cookieSet : cookieSets) {
            String directHeader = firstString(cookieSet, "Cookie", "cookie", "header", "cookies");
            if (directHeader != null && directHeader.contains("=")) {
                headers.add(normalizeHeader(directHeader));
                continue;
            }

            List<String> pairs = new ArrayList<>();
            for (Map.Entry<String, Object> entry : cookieSet.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                if (name == null || name.isBlank() || value == null) {
                    continue;
                }
                String stringValue = String.valueOf(value).trim();
                if (stringValue.isBlank()) {
                    continue;
                }
                pairs.add(name.trim() + "=" + stringValue);
            }
            if (!pairs.isEmpty()) {
                headers.add(normalizeHeader(String.join("; ", pairs)));
            }
        }
        return headers;
    }

    private static String firstString(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue.trim();
            }
        }
        return null;
    }

    private static String normalizeHeader(String header) {
        return header
                .replace("\r", "")
                .replace("\n", "; ")
                .replaceAll(";\\s*;", ";")
                .trim();
    }
}
