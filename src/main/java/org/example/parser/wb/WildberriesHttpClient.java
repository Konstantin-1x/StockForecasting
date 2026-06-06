package org.example.parser.wb;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

@Component
public class WildberriesHttpClient {

    private static final Logger log = LoggerFactory.getLogger(WildberriesHttpClient.class);
    private static final int MAX_LOGGED_URL_LENGTH = 180;
    private static final ProxyAuthenticator PROXY_AUTHENTICATOR = new ProxyAuthenticator();
    private static final Object AUTHENTICATOR_LOCK = new Object();
    private static volatile boolean proxyAuthenticatorInstalled;

    private final WildberriesParserProperties properties;
    private final Object cookieReloadLock = new Object();
    private final WildberriesProxyPool proxyPool;
    private final AtomicLong nextDetailRequestAtMillis = new AtomicLong();
    private volatile CookieRotator cookieRotator;
    private volatile String cookieFileSignature = "";

    public WildberriesHttpClient(WildberriesParserProperties properties) {
        this.properties = properties;
        installProxyAuthenticator();
        this.cookieRotator = loadCookieRotator();
        this.cookieFileSignature = cookieFileSignature(properties.getCookieFile());
        this.proxyPool = properties.isProxyEnabled()
                ? WildberriesProxyFileLoader.load(properties)
                : WildberriesProxyPool.direct();
        logCookieDiagnostics();
        if (!properties.isProxyEnabled()) {
            log.info("WB proxies are disabled. Requests will be sent directly.");
        }
    }

    public String getJson(String url, String referer) throws IOException {
        int maxAttempts = proxyPool.isDirect() ? 1 : Math.max(1, properties.getProxyMaxAttempts());
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            WildberriesProxy proxy = proxyPool.randomProxy();
            try {
                String json = executeJsonRequest(url, referer, proxy);
                proxyPool.recordSuccess(proxy);
                return json;
            } catch (WildberriesHttpStatusException e) {
                if (isProxyRetryableHttpStatus(e.getStatusCode()) && proxy != null) {
                    proxyPool.recordFailure(proxy);
                    if (attempt >= maxAttempts) {
                        throw e;
                    }
                    log.debug("WB GET {} via {} returned HTTP {} on attempt {}/{}. Retrying with another proxy.",
                            compactUrl(url),
                            proxy.safeLabel(),
                            e.getStatusCode(),
                            attempt,
                            maxAttempts);
                    continue;
                }
                proxyPool.recordSuccess(proxy);
                throw e;
            } catch (IOException e) {
                proxyPool.recordFailure(proxy);
                lastFailure = withProxyContext(e, proxy);
                if (attempt >= maxAttempts) {
                    break;
                }
                log.debug("WB GET {} via {} failed on attempt {}/{}: {}. Retrying with another proxy.",
                        compactUrl(url),
                        proxy == null ? "direct connection" : proxy.safeLabel(),
                        attempt,
                        maxAttempts,
                        e.getMessage());
            }
        }
        throw lastFailure == null ? new IOException("WB request failed: " + url) : lastFailure;
    }

    private String executeJsonRequest(String url, String referer, WildberriesProxy proxy) throws IOException {
        refreshCookiesIfFileChanged();
        throttleProductDetailRequests(url);
        RawResponse response = proxy != null && proxy.secure()
                ? executeSecureProxyJsonRequest(url, referer, proxy)
                : executeJsoupJsonRequest(url, referer, proxy);
        int statusCode = response.statusCode();
        String body = response.body();
        cookieRotator.recordStatusCode(statusCode);
        if (statusCode == 498) {
            refreshCookiesIfFileChanged();
        }
        if (statusCode < 200 || statusCode >= 300) {
            logHttpStatus(url, statusCode, proxy);
            throw new WildberriesHttpStatusException(statusCode, url, retryAfter(response.retryAfter()));
        }
        logSuccess(url, statusCode, body == null ? 0 : body.length(), proxy);
        if (body == null || body.isBlank()) {
            throw new IOException("Wildberries returned empty body for " + url);
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            throw new IOException("Wildberries returned non-JSON body for " + url);
        }
        return body;
    }

    private RawResponse executeJsoupJsonRequest(String url, String referer, WildberriesProxy proxy) throws IOException {
        Duration timeout = requestTimeout(proxy);
        Connection connection = Jsoup.connect(url)
                .userAgent(properties.getUserAgent())
                .header("Accept", "*/*")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Origin", "https://www.wildberries.ru")
                .header("Referer", referer == null || referer.isBlank() ? "https://www.wildberries.ru/" : referer)
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("x-requested-with", "XMLHttpRequest")
                .header("x-spa-version", properties.getSpaVersion())
                .method(Connection.Method.GET)
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .timeout(Math.toIntExact(timeout.toMillis()))
                .followRedirects(true)
                .maxBodySize(0);

        if (proxy != null) {
            connection.proxy(proxy.toJavaProxy());
            if (proxy.hasCredentials()) {
                connection.header("Proxy-Authorization", basicProxyAuthorization(proxy));
            }
        }

        addOptionalHeader(connection, "deviceid", properties.getDeviceId());
        addCookies(connection, cookieRotator.currentHeader());

        Connection.Response response;
        try (ProxyAuthenticator.Scope ignored = PROXY_AUTHENTICATOR.use(proxy)) {
            response = connection.execute();
        }
        return new RawResponse(response.statusCode(), response.body(), response.header("Retry-After"));
    }

    private RawResponse executeSecureProxyJsonRequest(String url, String referer, WildberriesProxy proxy) throws IOException {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Secure WB proxy mode supports only HTTPS target URLs: " + url);
        }

        String targetHost = uri.getHost();
        int targetPort = uri.getPort() > 0 ? uri.getPort() : 443;
        String targetPath = targetPath(uri);
        int timeoutMillis = safeTimeoutMillis(requestTimeout(proxy));
        SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();

        Socket tcpSocket = new Socket();
        tcpSocket.connect(new InetSocketAddress(proxy.host(), proxy.port()), timeoutMillis);
        tcpSocket.setSoTimeout(timeoutMillis);

        try (SSLSocket proxySocket = (SSLSocket) sslSocketFactory.createSocket(tcpSocket, proxy.host(), proxy.port(), true)) {
            configureSni(proxySocket, proxy.host());
            proxySocket.setSoTimeout(timeoutMillis);
            proxySocket.startHandshake();

            writeSecureProxyConnect(proxySocket, proxy, targetHost, targetPort);
            RawHeaders connectHeaders = readHeaders(proxySocket.getInputStream());
            if (connectHeaders.statusCode() != 200) {
                return new RawResponse(connectHeaders.statusCode(), "", connectHeaders.firstHeader("retry-after"));
            }

            try (SSLSocket targetSocket = (SSLSocket) sslSocketFactory.createSocket(proxySocket, targetHost, targetPort, false)) {
                configureSni(targetSocket, targetHost);
                targetSocket.setSoTimeout(timeoutMillis);
                targetSocket.startHandshake();
                writeTargetGet(targetSocket, targetHost, targetPath, referer);

                InputStream input = targetSocket.getInputStream();
                RawHeaders responseHeaders = readHeaders(input);
                byte[] bodyBytes = readBody(input, responseHeaders);
                String body = decodeBody(bodyBytes, responseHeaders);
                return new RawResponse(responseHeaders.statusCode(), body, responseHeaders.firstHeader("retry-after"));
            }
        }
    }

    private void writeSecureProxyConnect(SSLSocket socket,
                                         WildberriesProxy proxy,
                                         String targetHost,
                                         int targetPort) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1);
        writer.write("CONNECT " + targetHost + ":" + targetPort + " HTTP/1.1\r\n");
        writer.write("Host: " + targetHost + ":" + targetPort + "\r\n");
        writer.write("User-Agent: " + properties.getUserAgent() + "\r\n");
        if (proxy.hasCredentials()) {
            writer.write("Proxy-Authorization: " + basicProxyAuthorization(proxy) + "\r\n");
        }
        writer.write("Proxy-Connection: Keep-Alive\r\n");
        writer.write("\r\n");
        writer.flush();
    }

    private void writeTargetGet(SSLSocket socket,
                                String targetHost,
                                String targetPath,
                                String referer) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1);
        writer.write("GET " + targetPath + " HTTP/1.1\r\n");
        writer.write("Host: " + targetHost + "\r\n");
        writer.write("User-Agent: " + properties.getUserAgent() + "\r\n");
        writer.write("Accept: */*\r\n");
        writer.write("Accept-Encoding: identity\r\n");
        writer.write("Accept-Language: ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7\r\n");
        writer.write("Origin: https://www.wildberries.ru\r\n");
        writer.write("Referer: " + (referer == null || referer.isBlank() ? "https://www.wildberries.ru/" : referer) + "\r\n");
        writer.write("Sec-Fetch-Dest: empty\r\n");
        writer.write("Sec-Fetch-Mode: cors\r\n");
        writer.write("Sec-Fetch-Site: same-origin\r\n");
        writer.write("x-requested-with: XMLHttpRequest\r\n");
        writer.write("x-spa-version: " + properties.getSpaVersion() + "\r\n");
        if (properties.getDeviceId() != null && !properties.getDeviceId().isBlank()) {
            writer.write("deviceid: " + properties.getDeviceId() + "\r\n");
        }
        String cookieHeader = cookieRotator.currentHeader();
        if (cookieHeader != null && !cookieHeader.isBlank()) {
            writer.write("Cookie: " + cookieHeader + "\r\n");
        }
        writer.write("Connection: close\r\n");
        writer.write("\r\n");
        writer.flush();
    }

    private static void configureSni(SSLSocket socket, String host) {
        if (host == null || host.isBlank() || isIpAddress(host)) {
            return;
        }
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setServerNames(List.of(new SNIHostName(host)));
        socket.setSSLParameters(parameters);
    }

    private static boolean isIpAddress(String host) {
        return host.chars().allMatch(ch -> Character.isDigit(ch) || ch == '.')
                || host.contains(":");
    }

    private static String targetPath(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        String query = uri.getRawQuery();
        return query == null || query.isBlank() ? path : path + "?" + query;
    }

    private static RawHeaders readHeaders(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int previous3 = -1;
        int previous2 = -1;
        int previous1 = -1;
        int current;
        while ((current = input.read()) != -1) {
            buffer.write(current);
            if (previous3 == '\r' && previous2 == '\n' && previous1 == '\r' && current == '\n') {
                String headerText = new String(buffer.toByteArray(), StandardCharsets.ISO_8859_1);
                return RawHeaders.parse(headerText);
            }
            previous3 = previous2;
            previous2 = previous1;
            previous1 = current;
            if (buffer.size() > 64 * 1024) {
                throw new IOException("HTTP headers are too large");
            }
        }
        throw new EOFException("Connection closed before HTTP headers were received");
    }

    private static byte[] readBody(InputStream input, RawHeaders headers) throws IOException {
        if ("chunked".equalsIgnoreCase(headers.firstHeader("transfer-encoding"))) {
            return readChunkedBody(input);
        }
        String contentLength = headers.firstHeader("content-length");
        if (contentLength != null && !contentLength.isBlank()) {
            int length = Integer.parseInt(contentLength.trim());
            return input.readNBytes(length);
        }
        return input.readAllBytes();
    }

    private static byte[] readChunkedBody(InputStream input) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            String chunkHeader = readAsciiLine(input);
            int separator = chunkHeader.indexOf(';');
            String sizeValue = separator >= 0 ? chunkHeader.substring(0, separator) : chunkHeader;
            int size = Integer.parseInt(sizeValue.trim(), 16);
            if (size == 0) {
                while (!readAsciiLine(input).isEmpty()) {
                    // Discard trailers.
                }
                return body.toByteArray();
            }
            body.write(input.readNBytes(size));
            readExpectedCrlf(input);
        }
    }

    private static String readAsciiLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        int current;
        while ((current = input.read()) != -1) {
            if (previous == '\r' && current == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, Math.max(0, bytes.length - 1), StandardCharsets.ISO_8859_1);
            }
            line.write(current);
            previous = current;
            if (line.size() > 8 * 1024) {
                throw new IOException("HTTP line is too large");
            }
        }
        throw new EOFException("Connection closed while reading HTTP line");
    }

    private static void readExpectedCrlf(InputStream input) throws IOException {
        int cr = input.read();
        int lf = input.read();
        if (cr != '\r' || lf != '\n') {
            throw new IOException("Invalid chunk delimiter");
        }
    }

    private static String decodeBody(byte[] bodyBytes, RawHeaders headers) throws IOException {
        String encoding = headers.firstHeader("content-encoding");
        if (encoding != null && "gzip".equalsIgnoreCase(encoding.trim())) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bodyBytes))) {
                return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return new String(bodyBytes, StandardCharsets.UTF_8);
    }

    private static void installProxyAuthenticator() {
        if (proxyAuthenticatorInstalled) {
            return;
        }
        synchronized (AUTHENTICATOR_LOCK) {
            if (proxyAuthenticatorInstalled) {
                return;
            }
            System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
            System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
            Authenticator.setDefault(PROXY_AUTHENTICATOR);
            proxyAuthenticatorInstalled = true;
        }
    }

    private static void addOptionalHeader(Connection connection, String name, String value) {
        if (value != null && !value.isBlank()) {
            connection.header(name, value);
        }
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

    private CookieRotator loadCookieRotator() {
        return CookieFileLoader.load(
                properties.getCookieFile(),
                properties.getCookieSwitchWindowSize(),
                properties.getCookieSwitchMinSamples(),
                properties.getCookieSwitchHttp498Threshold()
        );
    }

    private void refreshCookiesIfFileChanged() {
        Path cookieFile = properties.getCookieFile();
        String signature = cookieFileSignature(cookieFile);
        if (signature.equals(cookieFileSignature)) {
            return;
        }
        synchronized (cookieReloadLock) {
            String currentSignature = cookieFileSignature(cookieFile);
            if (currentSignature.equals(cookieFileSignature)) {
                return;
            }
            CookieRotator reloaded = loadCookieRotator();
            cookieRotator = reloaded;
            cookieFileSignature = currentSignature;
            logCookieDiagnostics();
        }
    }

    private static String cookieFileSignature(Path cookieFile) {
        if (cookieFile == null || !Files.exists(cookieFile)) {
            return "missing";
        }
        try {
            return Files.getLastModifiedTime(cookieFile).toMillis() + ":" + Files.size(cookieFile);
        } catch (IOException e) {
            return "unreadable:" + e.getClass().getSimpleName();
        }
    }

    private void throttleProductDetailRequests(String url) throws IOException {
        if (!isProductDetailUrl(url)) {
            return;
        }
        Duration spacing = properties.getRequestDelay();
        if (spacing == null || spacing.isZero() || spacing.isNegative()) {
            return;
        }
        long spacingMillis = Math.max(1L, spacing.toMillis());
        while (true) {
            long now = System.currentTimeMillis();
            long previous = nextDetailRequestAtMillis.get();
            long scheduled = Math.max(now, previous);
            long next = scheduled + spacingMillis;
            if (!nextDetailRequestAtMillis.compareAndSet(previous, next)) {
                continue;
            }
            long sleepMillis = scheduled - now;
            if (sleepMillis > 0) {
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for WB detail request throttle", e);
                }
            }
            return;
        }
    }

    private static boolean isProductDetailUrl(String url) {
        return url != null && url.contains("/__internal/u-card/cards/v4/detail");
    }

    private void logCookieDiagnostics() {
        int cookieSets = cookieRotator.size();
        if (cookieSets <= 1) {
            log.warn("Loaded only {} WB cookie set(s). HTTP 498 cookie rotation is limited.", cookieSets);
        }
        int pairs = countCookiePairs(cookieRotator.currentHeader());
        if (pairs > 0 && pairs <= 2) {
            log.warn("Current WB cookie set contains only {} cookie pair(s). A fuller browser cookie export may be required for stable detail requests.", pairs);
        }
    }

    private static int countCookiePairs(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String cookie : cookieHeader.split(";")) {
            String trimmed = cookie.trim();
            if (!trimmed.isEmpty() && trimmed.contains("=")) {
                count++;
            }
        }
        return count;
    }

    private static IOException withProxyContext(IOException exception, WildberriesProxy proxy) {
        if (proxy == null) {
            return exception;
        }
        return new IOException("Proxy " + proxy.safeLabel() + " failed: " + exception.getMessage(), exception);
    }

    private Duration requestTimeout(WildberriesProxy proxy) {
        Duration timeout = proxy == null ? properties.getRequestTimeout() : properties.getProxyRequestTimeout();
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            return Duration.ofSeconds(proxy == null ? 20 : 8);
        }
        return timeout;
    }

    private static int safeTimeoutMillis(Duration timeout) {
        Duration safeTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(8)
                : timeout;
        long millis = safeTimeout.toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    private static String basicProxyAuthorization(WildberriesProxy proxy) {
        String username = proxy.username() == null ? "" : proxy.username();
        String password = proxy.password() == null ? "" : proxy.password();
        String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.ISO_8859_1));
        return "Basic " + token;
    }

    private static String compactUrl(String url) {
        if (url.length() <= MAX_LOGGED_URL_LENGTH) {
            return url;
        }
        return url.substring(0, MAX_LOGGED_URL_LENGTH - 3) + "...";
    }

    private static Duration retryAfter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            return Duration.ofSeconds(Math.max(0, seconds));
        } catch (NumberFormatException ignored) {
            // Retry-After can also be an HTTP date.
        }
        try {
            ZonedDateTime retryAt = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            Duration duration = Duration.between(ZonedDateTime.now(retryAt.getZone()), retryAt);
            return duration.isNegative() ? Duration.ZERO : duration;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static boolean isProxyHttpStatus(int statusCode) {
        return statusCode == 407 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private static boolean isProxyRetryableHttpStatus(int statusCode) {
        return statusCode == 429 || isProxyHttpStatus(statusCode);
    }

    private void logHttpStatus(String url, int statusCode, WildberriesProxy proxy) {
        if (proxy == null) {
            log.warn("WB GET {} -> HTTP {}", compactUrl(url), statusCode);
            return;
        }
        log.warn("WB GET {} via {} -> HTTP {}", compactUrl(url), proxy.safeLabel(), statusCode);
    }

    private void logSuccess(String url, int statusCode, int bodyLength, WildberriesProxy proxy) {
        if (properties.isLogSuccessfulRequests()) {
            if (proxy == null) {
                log.info("WB GET {} -> HTTP {} ({} chars)", compactUrl(url), statusCode, bodyLength);
            } else {
                log.info("WB GET {} via {} -> HTTP {} ({} chars)", compactUrl(url), proxy.safeLabel(), statusCode, bodyLength);
            }
        } else {
            if (proxy == null) {
                log.debug("WB GET {} -> HTTP {} ({} chars)", compactUrl(url), statusCode, bodyLength);
            } else {
                log.debug("WB GET {} via {} -> HTTP {} ({} chars)", compactUrl(url), proxy.safeLabel(), statusCode, bodyLength);
            }
        }
    }

    private record RawResponse(int statusCode, String body, String retryAfter) {
    }

    private record RawHeaders(int statusCode, Map<String, List<String>> headers) {

        private static RawHeaders parse(String headerText) throws IOException {
            String[] lines = headerText.split("\\r?\\n");
            if (lines.length == 0 || lines[0].isBlank()) {
                throw new IOException("Empty HTTP response headers");
            }
            String[] statusParts = lines[0].split(" ", 3);
            if (statusParts.length < 2) {
                throw new IOException("Invalid HTTP status line: " + lines[0]);
            }

            int statusCode;
            try {
                statusCode = Integer.parseInt(statusParts[1]);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid HTTP status code: " + lines[0], e);
            }

            Map<String, List<String>> headers = new LinkedHashMap<>();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                int separator = line.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(separator + 1).trim();
                headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
            }
            return new RawHeaders(statusCode, headers);
        }

        private String firstHeader(String name) {
            if (name == null) {
                return null;
            }
            List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }

    private static final class ProxyAuthenticator extends Authenticator {

        private final ThreadLocal<WildberriesProxy> activeProxy = new ThreadLocal<>();

        private Scope use(WildberriesProxy proxy) {
            if (proxy == null || !proxy.hasCredentials()) {
                return () -> {
                };
            }
            activeProxy.set(proxy);
            return activeProxy::remove;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            WildberriesProxy proxy = activeProxy.get();
            if (proxy == null || getRequestorType() != RequestorType.PROXY || !matches(proxy)) {
                return null;
            }
            String username = proxy.username() == null ? "" : proxy.username();
            String password = proxy.password() == null ? "" : proxy.password();
            return new PasswordAuthentication(username, password.toCharArray());
        }

        private boolean matches(WildberriesProxy proxy) {
            int requestingPort = getRequestingPort();
            if (requestingPort > 0 && requestingPort != proxy.port()) {
                return false;
            }
            String requestingHost = getRequestingHost();
            return requestingHost == null || requestingHost.equalsIgnoreCase(proxy.host());
        }

        private interface Scope extends AutoCloseable {
            @Override
            void close();
        }
    }

    Map<String, Object> diagnostics() {
        return Map.of(
                "cookieSets", cookieRotator.size(),
                "currentCookieSet", cookieRotator.currentIndex() + 1,
                "cookieHttp498WindowSamples", cookieRotator.recentSamples(),
                "cookieHttp498WindowHits", cookieRotator.recentHttp498Count(),
                "proxyEnabled", properties.isProxyEnabled(),
                "proxyMode", proxyPool.isDirect() ? "direct" : "proxy",
                "proxies", proxyPool.size(),
                "proxiesAvailable", proxyPool.availableCount(),
                "proxiesCoolingDown", proxyPool.coolingDownCount()
        );
    }
}
