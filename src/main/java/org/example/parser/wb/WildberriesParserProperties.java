package org.example.parser.wb;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "wb.parser")
public class WildberriesParserProperties {

    private String promotionsUrl = "https://static-basket-01.wbbasket.ru/vol0/data/promotions/rubli-za-otzyvy-v3.json";
    private Path cookieFile = Path.of("cookies.txt");
    private Path proxyFile = Path.of("proxies.txt");
    private String proxyUsername = "";
    private String proxyPassword = "";
    private boolean proxySecure = true;
    private boolean proxyEnabled = true;
    private int proxyMaxAttempts = 10;
    private Duration proxyFailureCooldown = Duration.ofMinutes(5);
    private Duration proxyRequestTimeout = Duration.ofSeconds(8);
    private boolean proxyHealthCheckEnabled = true;
    private Duration proxyHealthCheckTimeout = Duration.ofSeconds(2);
    private int proxyHealthCheckParallelism = 32;
    private int cookieSwitchWindowSize = 50;
    private int cookieSwitchMinSamples = 20;
    private double cookieSwitchHttp498Threshold = 0.20;
    private int maxCategories = 0;
    private int maxPagesPerCategory = 0;
    private Duration requestTimeout = Duration.ofSeconds(20);
    private Duration requestDelay = Duration.ofMillis(400);
    private boolean logSuccessfulRequests = false;
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:150.0) Gecko/20100101 Firefox/150.0";
    private String spaVersion = "14.8.1";
    private String deviceId = "";
    private boolean continuousScanEnabled = true;
    private boolean monitoringEnabled = false;
    private boolean baselineOnStartup = true;
    private int scanParallelism = 20;
    private int monitorBatchSize = 5_000;
    private int monitorParallelism = 20;
    private long scanInitialDelayMs = 3_600_000;
    private long scanFixedDelayMs = 3_600_000;
    private long monitorInitialDelayMs = 60_000;
    private long monitorFixedDelayMs = 3_600_000;
    private Duration productMeasurementDelay = Duration.ofHours(1);
    private Duration productRateLimitRetryDelay = Duration.ofMinutes(1);

    public String getPromotionsUrl() {
        return promotionsUrl;
    }

    public void setPromotionsUrl(String promotionsUrl) {
        this.promotionsUrl = promotionsUrl;
    }

    public Path getCookieFile() {
        return cookieFile;
    }

    public void setCookieFile(Path cookieFile) {
        this.cookieFile = cookieFile;
    }

    public Path getProxyFile() {
        return proxyFile;
    }

    public void setProxyFile(Path proxyFile) {
        this.proxyFile = proxyFile;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public boolean isProxySecure() {
        return proxySecure;
    }

    public void setProxySecure(boolean proxySecure) {
        this.proxySecure = proxySecure;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public void setProxyEnabled(boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
    }

    public int getProxyMaxAttempts() {
        return proxyMaxAttempts;
    }

    public void setProxyMaxAttempts(int proxyMaxAttempts) {
        this.proxyMaxAttempts = proxyMaxAttempts;
    }

    public Duration getProxyFailureCooldown() {
        return proxyFailureCooldown;
    }

    public void setProxyFailureCooldown(Duration proxyFailureCooldown) {
        this.proxyFailureCooldown = proxyFailureCooldown;
    }

    public Duration getProxyRequestTimeout() {
        return proxyRequestTimeout;
    }

    public void setProxyRequestTimeout(Duration proxyRequestTimeout) {
        this.proxyRequestTimeout = proxyRequestTimeout;
    }

    public boolean isProxyHealthCheckEnabled() {
        return proxyHealthCheckEnabled;
    }

    public void setProxyHealthCheckEnabled(boolean proxyHealthCheckEnabled) {
        this.proxyHealthCheckEnabled = proxyHealthCheckEnabled;
    }

    public Duration getProxyHealthCheckTimeout() {
        return proxyHealthCheckTimeout;
    }

    public void setProxyHealthCheckTimeout(Duration proxyHealthCheckTimeout) {
        this.proxyHealthCheckTimeout = proxyHealthCheckTimeout;
    }

    public int getProxyHealthCheckParallelism() {
        return proxyHealthCheckParallelism;
    }

    public void setProxyHealthCheckParallelism(int proxyHealthCheckParallelism) {
        this.proxyHealthCheckParallelism = proxyHealthCheckParallelism;
    }

    public int getCookieSwitchWindowSize() {
        return cookieSwitchWindowSize;
    }

    public void setCookieSwitchWindowSize(int cookieSwitchWindowSize) {
        this.cookieSwitchWindowSize = cookieSwitchWindowSize;
    }

    public int getCookieSwitchMinSamples() {
        return cookieSwitchMinSamples;
    }

    public void setCookieSwitchMinSamples(int cookieSwitchMinSamples) {
        this.cookieSwitchMinSamples = cookieSwitchMinSamples;
    }

    public double getCookieSwitchHttp498Threshold() {
        return cookieSwitchHttp498Threshold;
    }

    public void setCookieSwitchHttp498Threshold(double cookieSwitchHttp498Threshold) {
        this.cookieSwitchHttp498Threshold = cookieSwitchHttp498Threshold;
    }

    public int getMaxCategories() {
        return maxCategories;
    }

    public void setMaxCategories(int maxCategories) {
        this.maxCategories = maxCategories;
    }

    public int getMaxPagesPerCategory() {
        return maxPagesPerCategory;
    }

    public void setMaxPagesPerCategory(int maxPagesPerCategory) {
        this.maxPagesPerCategory = maxPagesPerCategory;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getRequestDelay() {
        return requestDelay;
    }

    public void setRequestDelay(Duration requestDelay) {
        this.requestDelay = requestDelay;
    }

    public boolean isLogSuccessfulRequests() {
        return logSuccessfulRequests;
    }

    public void setLogSuccessfulRequests(boolean logSuccessfulRequests) {
        this.logSuccessfulRequests = logSuccessfulRequests;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getSpaVersion() {
        return spaVersion;
    }

    public void setSpaVersion(String spaVersion) {
        this.spaVersion = spaVersion;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public boolean isContinuousScanEnabled() {
        return continuousScanEnabled;
    }

    public void setContinuousScanEnabled(boolean continuousScanEnabled) {
        this.continuousScanEnabled = continuousScanEnabled;
    }

    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    public void setMonitoringEnabled(boolean monitoringEnabled) {
        this.monitoringEnabled = monitoringEnabled;
    }

    public boolean isBaselineOnStartup() {
        return baselineOnStartup;
    }

    public void setBaselineOnStartup(boolean baselineOnStartup) {
        this.baselineOnStartup = baselineOnStartup;
    }

    public int getScanParallelism() {
        return scanParallelism;
    }

    public void setScanParallelism(int scanParallelism) {
        this.scanParallelism = scanParallelism;
    }

    public int getMonitorBatchSize() {
        return monitorBatchSize;
    }

    public void setMonitorBatchSize(int monitorBatchSize) {
        this.monitorBatchSize = monitorBatchSize;
    }

    public int getMonitorParallelism() {
        return monitorParallelism;
    }

    public void setMonitorParallelism(int monitorParallelism) {
        this.monitorParallelism = monitorParallelism;
    }

    public long getScanInitialDelayMs() {
        return scanInitialDelayMs;
    }

    public void setScanInitialDelayMs(long scanInitialDelayMs) {
        this.scanInitialDelayMs = scanInitialDelayMs;
    }

    public long getScanFixedDelayMs() {
        return scanFixedDelayMs;
    }

    public void setScanFixedDelayMs(long scanFixedDelayMs) {
        this.scanFixedDelayMs = scanFixedDelayMs;
    }

    public long getMonitorInitialDelayMs() {
        return monitorInitialDelayMs;
    }

    public void setMonitorInitialDelayMs(long monitorInitialDelayMs) {
        this.monitorInitialDelayMs = monitorInitialDelayMs;
    }

    public long getMonitorFixedDelayMs() {
        return monitorFixedDelayMs;
    }

    public void setMonitorFixedDelayMs(long monitorFixedDelayMs) {
        this.monitorFixedDelayMs = monitorFixedDelayMs;
    }

    public Duration getProductMeasurementDelay() {
        return productMeasurementDelay;
    }

    public void setProductMeasurementDelay(Duration productMeasurementDelay) {
        this.productMeasurementDelay = productMeasurementDelay;
    }

    public Duration getProductRateLimitRetryDelay() {
        return productRateLimitRetryDelay;
    }

    public void setProductRateLimitRetryDelay(Duration productRateLimitRetryDelay) {
        this.productRateLimitRetryDelay = productRateLimitRetryDelay;
    }
}
