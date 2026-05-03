package org.example.parser.wb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class CookieRotator {

    private final List<String> cookieHeaders;
    private final AtomicInteger index = new AtomicInteger(0);

    CookieRotator(List<String> cookieHeaders) {
        this.cookieHeaders = cookieHeaders == null || cookieHeaders.isEmpty()
                ? List.of("")
                : List.copyOf(cookieHeaders);
    }

    String nextHeader() {
        if (cookieHeaders.size() == 1) {
            return cookieHeaders.getFirst();
        }
        int current = Math.floorMod(index.getAndIncrement(), cookieHeaders.size());
        return cookieHeaders.get(current);
    }

    int size() {
        return cookieHeaders.size();
    }
}
