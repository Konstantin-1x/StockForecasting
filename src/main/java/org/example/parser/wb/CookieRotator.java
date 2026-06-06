package org.example.parser.wb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

final class CookieRotator {

    private static final Logger log = LoggerFactory.getLogger(CookieRotator.class);

    private final List<String> cookieHeaders;
    private final int switchWindowSize;
    private final int switchMinSamples;
    private final double switchThreshold;
    private final Deque<Boolean> recentHttp498 = new ArrayDeque<>();
    private int recentHttp498Count = 0;
    private int index = 0;

    CookieRotator(List<String> cookieHeaders, int switchWindowSize, int switchMinSamples, double switchThreshold) {
        this.cookieHeaders = cookieHeaders == null || cookieHeaders.isEmpty()
                ? List.of("")
                : List.copyOf(cookieHeaders);
        this.switchWindowSize = Math.max(1, switchWindowSize);
        this.switchMinSamples = Math.max(1, Math.min(switchMinSamples, this.switchWindowSize));
        this.switchThreshold = Math.max(0.01, Math.min(1.0, switchThreshold));
    }

    synchronized String currentHeader() {
        return cookieHeaders.get(index);
    }

    synchronized void recordStatusCode(int statusCode) {
        if (cookieHeaders.size() <= 1) {
            return;
        }

        boolean isHttp498 = statusCode == 498;
        recentHttp498.addLast(isHttp498);
        if (isHttp498) {
            recentHttp498Count++;
        }
        while (recentHttp498.size() > switchWindowSize) {
            Boolean removed = recentHttp498.removeFirst();
            if (Boolean.TRUE.equals(removed)) {
                recentHttp498Count--;
            }
        }

        int samples = recentHttp498.size();
        if (samples < switchMinSamples) {
            return;
        }
        double ratio = (double) recentHttp498Count / samples;
        if (ratio >= switchThreshold) {
            index = (index + 1) % cookieHeaders.size();
            recentHttp498.clear();
            recentHttp498Count = 0;
            log.warn("Cookie set changed to #{}.", index + 1);
        }
    }

    int size() {
        return cookieHeaders.size();
    }

    synchronized int currentIndex() {
        return index;
    }

    synchronized int recentSamples() {
        return recentHttp498.size();
    }

    synchronized int recentHttp498Count() {
        return recentHttp498Count;
    }
}
