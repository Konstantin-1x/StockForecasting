package org.example.parser.wb;

import org.example.domain.MarketplaceMonitoringJob;
import org.example.domain.MarketplaceProductSnapshot;
import org.example.domain.TrackedMarketplaceProduct;
import org.example.repository.MarketplaceMonitoringJobRepository;
import org.example.repository.MarketplaceProductSnapshotRepository;
import org.example.repository.TrackedMarketplaceProductRepository;
import org.example.web.data.DataTransferState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Service
public class WildberriesProductMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(WildberriesProductMonitoringService.class);

    private final WildberriesParserProperties properties;
    private final WildberriesHttpClient httpClient;
    private final WildberriesResponseParser responseParser;
    private final MarketplaceMonitoringJobRepository monitoringJobRepository;
    private final MarketplaceProductSnapshotRepository snapshotRepository;
    private final TrackedMarketplaceProductRepository trackedProductRepository;
    private final ExecutorService wildberriesParserExecutor;
    private final DataTransferState transferState;
    private final Set<Long> runningJobs = ConcurrentHashMap.newKeySet();

    public WildberriesProductMonitoringService(WildberriesParserProperties properties,
                                               WildberriesHttpClient httpClient,
                                               WildberriesResponseParser responseParser,
                                               MarketplaceMonitoringJobRepository monitoringJobRepository,
                                               MarketplaceProductSnapshotRepository snapshotRepository,
                                               TrackedMarketplaceProductRepository trackedProductRepository,
                                               DataTransferState transferState,
                                               ExecutorService wildberriesParserExecutor) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.responseParser = responseParser;
        this.monitoringJobRepository = monitoringJobRepository;
        this.snapshotRepository = snapshotRepository;
        this.trackedProductRepository = trackedProductRepository;
        this.transferState = transferState;
        this.wildberriesParserExecutor = wildberriesParserExecutor;
    }

    @Scheduled(
            initialDelayString = "${wb.parser.monitor-initial-delay-ms:60000}",
            fixedDelayString = "${wb.parser.monitor-fixed-delay-ms:3600000}"
    )
    public void scheduledMonitoringRun() {
        if (!properties.isMonitoringEnabled()) {
            return;
        }
        if (transferState.isTransferInProgress()) {
            log.info("Wildberries monitoring run skipped: data transfer is running");
            return;
        }
        runDueJobs();
    }

    public WildberriesMonitoringRunResult runDueJobs() {
        if (transferState.isTransferInProgress()) {
            log.info("Wildberries monitoring run skipped: data transfer is running");
            return new WildberriesMonitoringRunResult(0, 0, runningJobs.size());
        }
        Instant now = Instant.now();
        int limit = Math.max(1, properties.getMonitorBatchSize());
        List<MarketplaceMonitoringJob> dueJobs = monitoringJobRepository.findDueJobs(
                now,
                PageRequest.of(0, limit)
        );

        int scheduled = 0;
        for (MarketplaceMonitoringJob job : dueJobs) {
            Long jobId = job.getId();
            if (jobId == null || !runningJobs.add(jobId)) {
                continue;
            }
            scheduled++;
            CompletableFuture.runAsync(() -> processJob(jobId), wildberriesParserExecutor)
                    .whenComplete((unused, error) -> runningJobs.remove(jobId));
        }

        if (scheduled > 0) {
            log.info("Scheduled {} Wildberries monitoring job(s), dueJobs={}, runningJobs={}",
                    scheduled,
                    dueJobs.size(),
                    runningJobs.size());
        }
        return new WildberriesMonitoringRunResult(dueJobs.size(), scheduled, runningJobs.size());
    }

    public int runningJobsCount() {
        return runningJobs.size();
    }

    private void processJob(Long jobId) {
        monitoringJobRepository.findWithProductById(jobId).ifPresent(job -> {
            if (job.isFinished()) {
                return;
            }
            try {
                collectSnapshot(job);
            } catch (IOException | RuntimeException e) {
                markJobFailed(jobId, e);
            }
        });
    }

    private void collectSnapshot(MarketplaceMonitoringJob job) throws IOException {
        TrackedMarketplaceProduct product = job.getProduct();
        String article = product.getMarketplaceArticle();
        String detailUrl = WildberriesProductDetailUrlBuilder.build(article);
        String json = httpClient.getJson(detailUrl, product.getCardUrl());
        WildberriesProductDetails details = responseParser.parseProductDetail(json, article);
        Instant collectedAt = Instant.now();

        MarketplaceProductSnapshot snapshot = new MarketplaceProductSnapshot();
        snapshot.setProduct(product);
        snapshot.setCollectedAt(collectedAt);
        snapshot.setPrice(details.price());
        snapshot.setStockQuantity(details.stockQuantity());
        snapshot.setFeedbackReward(details.feedbackReward());
        snapshot.setBenefitPercent(details.benefitPercent());
        snapshot.setRating(details.rating());
        snapshot.setReviewsCount(details.reviewsCount());
        snapshot.setMeasurementSource("PRODUCT_DETAIL");
        snapshot.setRawJson(details.rawJson());
        snapshotRepository.save(snapshot);

        product.setProductName(details.name());
        product.setSellerName(details.supplier());
        product.setSupplierId(details.supplierId());
        product.setDetailUrl(detailUrl);
        product.setDiscoveredPrice(details.price());
        product.setDiscoveredStock(details.stockQuantity());
        product.setFeedbackReward(details.feedbackReward());
        product.setBenefitPercent(details.benefitPercent());

        job.setLastRunAt(collectedAt);
        job.setLastError(null);
        job.setUpdatedAt(collectedAt);

        int stock = details.stockQuantity() == null ? 0 : details.stockQuantity();
        if (stock <= 0) {
            job.setFinished(true);
            product.setActive(false);
            product.setCompletedAt(collectedAt);
            trackedProductRepository.save(product);
            monitoringJobRepository.save(job);
            log.info("WB monitoring finished for article={}: stock is 0", article);
            return;
        }

        int completedSnapshots = job.getStage() + 1;
        job.setStage(completedSnapshots);
        job.setNextRunAt(calculateNextRunAt(collectedAt));
        trackedProductRepository.save(product);
        monitoringJobRepository.save(job);
        log.info("WB monitoring snapshot saved: article={}, stock={}, price={}, nextRunAt={}",
                article,
                stock,
                details.price(),
                job.getNextRunAt());
    }

    private void markJobFailed(Long jobId, RuntimeException error) {
        markJobFailed(jobId, (Exception) error);
    }

    private void markJobFailed(Long jobId, Exception error) {
        monitoringJobRepository.findWithProductById(jobId).ifPresent(job -> {
            Instant now = Instant.now();
            job.setLastError(limitError(error.getMessage()));
            job.setUpdatedAt(now);
            if (error instanceof WildberriesHttpStatusException httpError && httpError.isRateLimit()) {
                job.setNextRunAt(now.plus(properties.getProductRateLimitRetryDelay()));
                monitoringJobRepository.save(job);
                log.warn("WB product monitoring got HTTP 429 for article={}, retryAt={}",
                        job.getProduct().getMarketplaceArticle(),
                        job.getNextRunAt());
                return;
            }

            job.setNextRunAt(now.plus(properties.getRequestDelay().multipliedBy(5).plusSeconds(60)));
            monitoringJobRepository.save(job);
            log.warn("WB monitoring job {} failed, retryAt={}: {}",
                    jobId,
                    job.getNextRunAt(),
                    error.getMessage());
        });
    }

    private Instant calculateNextRunAt(Instant collectedAt) {
        return collectedAt.plus(properties.getProductMeasurementDelay());
    }

    private static String limitError(String error) {
        if (error == null) {
            return null;
        }
        if (error.length() <= 2000) {
            return error;
        }
        return error.substring(0, 2000);
    }
}
