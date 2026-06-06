package org.example.parser.wb;

import java.io.IOException;
import java.time.Duration;

public class WildberriesHttpStatusException extends IOException {

    private final int statusCode;
    private final String url;
    private final Duration retryAfter;

    public WildberriesHttpStatusException(int statusCode, String url, Duration retryAfter) {
        super("Wildberries returned HTTP " + statusCode + " for " + url);
        this.statusCode = statusCode;
        this.url = url;
        this.retryAfter = retryAfter;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getUrl() {
        return url;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    public boolean isRateLimit() {
        return statusCode == 429;
    }
}
