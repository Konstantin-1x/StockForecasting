package org.example.parser.wb;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class WildberriesProxyPool {

    private final List<ProxyState> proxies;
    private final Duration failureCooldown;

    WildberriesProxyPool(List<WildberriesProxy> proxies, Duration failureCooldown) {
        List<ProxyState> states = new ArrayList<>();
        if (proxies != null) {
            for (WildberriesProxy proxy : proxies) {
                states.add(new ProxyState(proxy));
            }
        }
        this.proxies = List.copyOf(states);
        this.failureCooldown = failureCooldown == null || failureCooldown.isNegative() || failureCooldown.isZero()
                ? Duration.ofMinutes(5)
                : failureCooldown;
    }

    static WildberriesProxyPool direct() {
        return new WildberriesProxyPool(List.of(), Duration.ofMinutes(5));
    }

    WildberriesProxy randomProxy() {
        if (proxies.isEmpty()) {
            return null;
        }
        List<ProxyState> available = availableProxies();
        List<ProxyState> candidates = available.isEmpty() ? proxies : available;
        int index = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(index).proxy;
    }

    void recordSuccess(WildberriesProxy proxy) {
        ProxyState state = find(proxy);
        if (state != null) {
            state.clearFailure();
        }
    }

    void recordFailure(WildberriesProxy proxy) {
        ProxyState state = find(proxy);
        if (state != null) {
            state.markFailedFor(failureCooldown);
        }
    }

    boolean isDirect() {
        return proxies.isEmpty();
    }

    int size() {
        return proxies.size();
    }

    int availableCount() {
        return availableProxies().size();
    }

    int coolingDownCount() {
        Instant now = Instant.now();
        int count = 0;
        for (ProxyState state : proxies) {
            if (state.isCoolingDown(now)) {
                count++;
            }
        }
        return count;
    }

    private List<ProxyState> availableProxies() {
        Instant now = Instant.now();
        List<ProxyState> available = new ArrayList<>();
        for (ProxyState state : proxies) {
            if (!state.isCoolingDown(now)) {
                available.add(state);
            }
        }
        return available;
    }

    private ProxyState find(WildberriesProxy proxy) {
        if (proxy == null) {
            return null;
        }
        for (ProxyState state : proxies) {
            if (state.proxy.equals(proxy)) {
                return state;
            }
        }
        return null;
    }

    private static final class ProxyState {
        private final WildberriesProxy proxy;
        private volatile Instant cooldownUntil = Instant.EPOCH;

        private ProxyState(WildberriesProxy proxy) {
            this.proxy = proxy;
        }

        private boolean isCoolingDown(Instant now) {
            return cooldownUntil.isAfter(now);
        }

        private void markFailedFor(Duration duration) {
            cooldownUntil = Instant.now().plus(duration);
        }

        private void clearFailure() {
            cooldownUntil = Instant.EPOCH;
        }
    }
}
