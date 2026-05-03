package org.example.parser.wb;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Component
public class WildberriesHttpClient {

    private static final Logger log = LoggerFactory.getLogger(WildberriesHttpClient.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:148.0) Gecko/20100101 Firefox/148.0";
    private static final int MAX_LOGGED_URL_LENGTH = 180;

    private final WildberriesParserProperties properties;
    private final CookieRotator cookieRotator;

    public WildberriesHttpClient(WildberriesParserProperties properties) {
        this.properties = properties;
        this.cookieRotator = CookieFileLoader.load(properties.getCookieFile());
    }

    public String getJson(String url, String referer) throws IOException {
        Duration timeout = properties.getRequestTimeout();
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", "*/*")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Origin", "https://www.wildberries.ru")
                .header("Referer", referer == null || referer.isBlank() ? "https://www.wildberries.ru/" : referer)
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("x-requested-with", "XMLHttpRequest")
                .header("x-spa-version", "14.0.7")
                .method(Connection.Method.GET)
                .ignoreContentType(true)
                .timeout(Math.toIntExact(timeout.toMillis()))
                .followRedirects(true)
                .maxBodySize(0);

        addCookies(connection, cookieRotator.nextHeader());

        Connection.Response response = connection.execute();
        int statusCode = response.statusCode();
        String body = response.body();
        if (statusCode < 200 || statusCode >= 300) {
            log.warn("WB GET {} -> HTTP {}", compactUrl(url), statusCode);
            throw new IOException("Wildberries returned HTTP " + statusCode + " for " + url);
        }
        log.info("WB GET {} -> HTTP {} ({} chars)", compactUrl(url), statusCode, body == null ? 0 : body.length());
        if (body == null || body.isBlank()) {
            throw new IOException("Wildberries returned empty body for " + url);
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            throw new IOException("Wildberries returned non-JSON body for " + url);
        }
        return body;
    }

    private static void addCookies(Connection connection, String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return;
        }
        for (String cookie : cookieHeader.split(";")) {
            String trimmed = cookie.trim();
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = trimmed.substring(0, separator).trim();
            String value = separator < trimmed.length() - 1 ? trimmed.substring(separator + 1).trim() : "";
            connection.cookie(name, value);
        }
    }

    private static String compactUrl(String url) {
        if (url.length() <= MAX_LOGGED_URL_LENGTH) {
            return url;
        }
        return url.substring(0, MAX_LOGGED_URL_LENGTH - 3) + "...";
    }

    Map<String, Object> diagnostics() {
        return Map.of("cookieSets", cookieRotator.size());
    }
}
