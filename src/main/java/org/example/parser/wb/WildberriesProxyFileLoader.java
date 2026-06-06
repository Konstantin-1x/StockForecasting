package org.example.parser.wb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class WildberriesProxyFileLoader {

    private static final Logger log = LoggerFactory.getLogger(WildberriesProxyFileLoader.class);

    private WildberriesProxyFileLoader() {
    }

    static WildberriesProxyPool load(WildberriesParserProperties properties) {
        return load(
                properties.getProxyFile(),
                properties.getProxyFailureCooldown(),
                properties.isProxyHealthCheckEnabled(),
                properties.getProxyHealthCheckTimeout(),
                properties.getProxyHealthCheckParallelism(),
                properties.getProxyUsername(),
                properties.getProxyPassword(),
                properties.isProxySecure()
        );
    }

    static WildberriesProxyPool load(Path proxyFile,
                                     Duration failureCooldown,
                                     boolean healthCheckEnabled,
                                     Duration healthCheckTimeout,
                                     int healthCheckParallelism) {
        return load(proxyFile, failureCooldown, healthCheckEnabled, healthCheckTimeout, healthCheckParallelism, null, null, false);
    }

    static WildberriesProxyPool load(Path proxyFile,
                                     Duration failureCooldown,
                                     boolean healthCheckEnabled,
                                     Duration healthCheckTimeout,
                                     int healthCheckParallelism,
                                     String defaultUsername,
                                     String defaultPassword) {
        return load(proxyFile, failureCooldown, healthCheckEnabled, healthCheckTimeout, healthCheckParallelism,
                defaultUsername, defaultPassword, false);
    }

    static WildberriesProxyPool load(Path proxyFile,
                                     Duration failureCooldown,
                                     boolean healthCheckEnabled,
                                     Duration healthCheckTimeout,
                                     int healthCheckParallelism,
                                     String defaultUsername,
                                     String defaultPassword,
                                     boolean defaultSecure) {
        if (proxyFile == null || !Files.exists(proxyFile)) {
            log.info("WB proxy file was not found: {}. Requests will be sent directly.", proxyFile);
            return WildberriesProxyPool.direct();
        }

        List<WildberriesProxy> proxies = new ArrayList<>();
        int skipped = 0;
        try {
            for (String rawLine : Files.readAllLines(proxyFile)) {
                String line = normalizeLine(rawLine);
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                WildberriesProxy proxy = parse(line, defaultSecure);
                if (proxy == null) {
                    skipped++;
                    continue;
                }
                proxies.add(withDefaultCredentials(proxy, defaultUsername, defaultPassword));
            }
        } catch (IOException e) {
            log.warn("Failed to read WB proxy file: {}. Requests will be sent directly.", proxyFile, e);
            return WildberriesProxyPool.direct();
        }

        if (proxies.isEmpty()) {
            log.warn("WB proxy file has no usable proxies: {}. Requests will be sent directly.", proxyFile);
            return WildberriesProxyPool.direct();
        }

        log.info("Loaded {} WB prox{} from {}{}", proxies.size(), proxies.size() == 1 ? "y" : "ies", proxyFile,
                skipped > 0 ? "; skipped invalid lines: " + skipped : "");

        if (healthCheckEnabled) {
            List<WildberriesProxy> liveProxies = healthCheck(proxies, healthCheckTimeout, healthCheckParallelism);
            if (liveProxies.isEmpty()) {
                log.warn("WB proxy health check found no reachable proxies out of {}. Requests will be sent directly.", proxies.size());
                return WildberriesProxyPool.direct();
            }
            log.info("WB proxy health check: {} of {} prox{} reachable", liveProxies.size(), proxies.size(),
                    proxies.size() == 1 ? "y is" : "ies are");
            proxies = liveProxies;
        }

        return new WildberriesProxyPool(proxies, failureCooldown);
    }

    private static WildberriesProxy withDefaultCredentials(WildberriesProxy proxy,
                                                           String defaultUsername,
                                                           String defaultPassword) {
        if (proxy.hasCredentials() || defaultUsername == null || defaultUsername.isBlank()) {
            return proxy;
        }
        return new WildberriesProxy(proxy.host(), proxy.port(), defaultUsername.trim(),
                defaultPassword == null ? "" : defaultPassword, proxy.secure());
    }

    private static String normalizeLine(String rawLine) {
        if (rawLine == null) {
            return "";
        }
        String line = rawLine.trim();
        if (line.regionMatches(true, 0, "http://", 0, "http://".length())
                || line.regionMatches(true, 0, "https://", 0, "https://".length())) {
            return line;
        }
        return line;
    }

    private static WildberriesProxy parse(String line, boolean defaultSecure) {
        WildberriesProxy fromUri = parseUri(line, defaultSecure);
        if (fromUri != null) {
            return fromUri;
        }

        String[] parts = line.split(":", 4);
        if (parts.length == 2) {
            Integer port = parsePort(parts[1]);
            return port == null ? null : new WildberriesProxy(parts[0].trim(), port, null, null, defaultSecure);
        }
        if (parts.length == 4) {
            Integer port = parsePort(parts[1]);
            return port == null
                    ? null
                    : new WildberriesProxy(parts[0].trim(), port, parts[2].trim(), parts[3].trim(), defaultSecure);
        }
        return null;
    }

    private static WildberriesProxy parseUri(String line, boolean defaultSecure) {
        boolean hasScheme = line.contains("://");
        String uriValue = hasScheme ? line : "http://" + line;
        try {
            URI uri = new URI(uriValue);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null || host.isBlank() || port < 1 || port > 65_535) {
                return null;
            }
            boolean secure = hasScheme ? "https".equalsIgnoreCase(uri.getScheme()) : defaultSecure;

            String userInfo = uri.getUserInfo();
            if (userInfo == null || userInfo.isBlank()) {
                return new WildberriesProxy(host, port, null, null, secure);
            }
            String[] credentials = userInfo.split(":", 2);
            String username = credentials[0];
            String password = credentials.length > 1 ? credentials[1] : "";
            return new WildberriesProxy(host, port, username, password, secure);
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static Integer parsePort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            return port >= 1 && port <= 65_535 ? port : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<WildberriesProxy> healthCheck(List<WildberriesProxy> proxies,
                                                      Duration timeout,
                                                      int parallelism) {
        if (proxies.isEmpty()) {
            return List.of();
        }
        int workers = Math.max(1, Math.min(Math.max(1, parallelism), proxies.size()));
        int timeoutMillis = safeTimeoutMillis(timeout);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Callable<WildberriesProxy>> tasks = proxies.stream()
                    .<Callable<WildberriesProxy>>map(proxy -> () -> reachable(proxy, timeoutMillis) ? proxy : null)
                    .toList();
            long checkBudgetMillis = (long) Math.ceil((double) proxies.size() / workers) * timeoutMillis + 1_000L;
            List<Future<WildberriesProxy>> futures = executor.invokeAll(tasks, checkBudgetMillis, TimeUnit.MILLISECONDS);
            List<WildberriesProxy> live = new ArrayList<>();
            for (Future<WildberriesProxy> future : futures) {
                if (future.isCancelled()) {
                    continue;
                }
                try {
                    WildberriesProxy proxy = future.get();
                    if (proxy != null) {
                        live.add(proxy);
                    }
                } catch (Exception ignored) {
                    // Unreachable proxies are simply excluded from the pool.
                }
            }
            return live;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("WB proxy health check was interrupted. Using unfiltered proxy list.");
            return proxies;
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean reachable(WildberriesProxy proxy, int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(proxy.host(), proxy.port()), timeoutMillis);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static int safeTimeoutMillis(Duration timeout) {
        Duration safeTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(2)
                : timeout;
        long millis = safeTimeout.toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }
}
