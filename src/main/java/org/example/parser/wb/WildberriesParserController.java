package org.example.parser.wb;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.example.repository.MarketplaceMonitoringJobRepository;
import org.example.repository.MarketplaceProductSnapshotRepository;
import org.example.repository.TrackedMarketplaceProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/parser/wildberries")
public class WildberriesParserController {

    private static final Logger log = LoggerFactory.getLogger(WildberriesParserController.class);
    private static final int RAW_JSON_PREVIEW_LIMIT = 2000;

    private final WildberriesParserService parserService;
    private final WildberriesHttpClient httpClient;
    private final WildberriesResponseParser responseParser;
    private final WildberriesProductDiscoveryService discoveryService;
    private final WildberriesProductMonitoringService monitoringService;
    private final TrackedMarketplaceProductRepository trackedProductRepository;
    private final MarketplaceMonitoringJobRepository monitoringJobRepository;
    private final MarketplaceProductSnapshotRepository snapshotRepository;

    public WildberriesParserController(WildberriesParserService parserService,
                                       WildberriesHttpClient httpClient,
                                       WildberriesResponseParser responseParser,
                                       WildberriesProductDiscoveryService discoveryService,
                                       WildberriesProductMonitoringService monitoringService,
                                       TrackedMarketplaceProductRepository trackedProductRepository,
                                       MarketplaceMonitoringJobRepository monitoringJobRepository,
                                       MarketplaceProductSnapshotRepository snapshotRepository) {
        this.parserService = parserService;
        this.httpClient = httpClient;
        this.responseParser = responseParser;
        this.discoveryService = discoveryService;
        this.monitoringService = monitoringService;
        this.trackedProductRepository = trackedProductRepository;
        this.monitoringJobRepository = monitoringJobRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @PostMapping("/rubles-for-reviews/import")
    public WildberriesImportResult importRublesForReviews(
            @RequestParam(required = false) @Min(1) @Max(500) Integer maxCategories,
            @RequestParam(required = false) @Min(1) @Max(100) Integer maxPagesPerCategory
    ) {
        return parserService.importRublesForReviews(maxCategories, maxPagesPerCategory);
    }

    @GetMapping("/diagnostics")
    public Map<String, Object> diagnostics() {
        Map<String, Object> diagnostics = new java.util.LinkedHashMap<>(httpClient.diagnostics());
        diagnostics.put("baselineReady", discoveryService.isBaselineReady());
        diagnostics.put("scanRunning", discoveryService.isScanRunning());
        diagnostics.put("seenArticlesInMemory", discoveryService.seenArticlesCount());
        diagnostics.put("trackedProducts", trackedProductRepository.count());
        diagnostics.put("activeTrackedProducts", trackedProductRepository.countByActiveTrue());
        diagnostics.put("activeMonitoringJobs", monitoringJobRepository.countByFinishedFalse());
        diagnostics.put("runningMonitoringJobs", monitoringService.runningJobsCount());
        diagnostics.put("productSnapshots", snapshotRepository.count());
        return diagnostics;
    }

    @PostMapping("/discovery/scan")
    public WildberriesDiscoveryResult runDiscoveryScan() {
        return discoveryService.runDiscoveryScan();
    }

    @PostMapping("/discovery/reset-baseline")
    public Map<String, Object> resetDiscoveryBaseline() {
        discoveryService.resetBaseline();
        return Map.of(
                "baselineReady", discoveryService.isBaselineReady(),
                "seenArticlesInMemory", discoveryService.seenArticlesCount()
        );
    }

    @PostMapping("/monitoring/run")
    public WildberriesMonitoringRunResult runMonitoring() {
        return monitoringService.runDueJobs();
    }

    @GetMapping("/product/test")
    public WildberriesProductDetailTestResult testProductDetail(
            @RequestParam String article,
            @RequestParam(defaultValue = "true") boolean includeRawPreview
    ) throws IOException {
        String normalizedArticle = article.trim();
        String cardUrl = WildberriesProductDetailUrlBuilder.cardUrl(normalizedArticle);
        String detailUrl = WildberriesProductDetailUrlBuilder.build(normalizedArticle);
        String json;
        try {
            json = httpClient.getJson(detailUrl, cardUrl);
        } catch (WildberriesHttpStatusException e) {
            log.warn("WB product detail test failed: article={}, httpStatus={}, retryAfter={}, message={}",
                    normalizedArticle,
                    e.getStatusCode(),
                    e.getRetryAfter(),
                    e.getMessage());
            return new WildberriesProductDetailTestResult(
                    false,
                    e.getStatusCode(),
                    e.getMessage(),
                    normalizedArticle,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    null,
                    Instant.now()
            );
        }
        WildberriesProductDetails details = responseParser.parseProductDetail(json, normalizedArticle);
        String rawPreview = includeRawPreview ? rawPreview(json) : null;

        WildberriesProductDetailTestResult result = new WildberriesProductDetailTestResult(
                true,
                200,
                null,
                details.article(),
                details.name(),
                details.supplier(),
                details.price(),
                details.stockQuantity(),
                details.feedbackReward(),
                details.benefitPercent(),
                details.rating(),
                details.reviewsCount(),
                json.length(),
                rawPreview,
                Instant.now()
        );

        log.info("WB product detail test result: article={}, name='{}', supplier='{}', price={}, stock={}, reward={}, rating={}, reviews={}, rawJsonLength={}",
                result.article(),
                result.name(),
                result.supplier(),
                result.price(),
                result.stockQuantity(),
                result.feedbackReward(),
                result.rating(),
                result.reviewsCount(),
                result.rawJsonLength());
        if (includeRawPreview) {
            log.info("WB product detail test raw preview for article={}: {}", result.article(), result.rawJsonPreview());
        }
        return result;
    }

    private static String rawPreview(String json) {
        if (json.length() <= RAW_JSON_PREVIEW_LIMIT) {
            return json;
        }
        return json.substring(0, RAW_JSON_PREVIEW_LIMIT) + "...";
    }
}
