package org.example.parser.wb;

import org.example.domain.CompetitorOffer;
import org.example.domain.MarketplaceMonitoringJob;
import org.example.domain.ProductCategory;
import org.example.domain.TrackedMarketplaceProduct;
import org.example.repository.CompetitorOfferRepository;
import org.example.repository.MarketplaceMonitoringJobRepository;
import org.example.repository.ProductCategoryRepository;
import org.example.repository.TrackedMarketplaceProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class WildberriesProductDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(WildberriesProductDiscoveryService.class);
    private static final int WB_PAGE_SIZE = 100;

    private final WildberriesParserProperties properties;
    private final WildberriesHttpClient httpClient;
    private final WildberriesResponseParser responseParser;
    private final ProductCategoryRepository categoryRepository;
    private final CompetitorOfferRepository offerRepository;
    private final TrackedMarketplaceProductRepository trackedProductRepository;
    private final MarketplaceMonitoringJobRepository monitoringJobRepository;
    private final ExecutorService wildberriesParserExecutor;
    private final Set<String> seenArticles = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean baselineReady = new AtomicBoolean(false);
    private final AtomicBoolean scanRunning = new AtomicBoolean(false);

    public WildberriesProductDiscoveryService(WildberriesParserProperties properties,
                                              WildberriesHttpClient httpClient,
                                              WildberriesResponseParser responseParser,
                                              ProductCategoryRepository categoryRepository,
                                              CompetitorOfferRepository offerRepository,
                                              TrackedMarketplaceProductRepository trackedProductRepository,
                                              MarketplaceMonitoringJobRepository monitoringJobRepository,
                                              ExecutorService wildberriesParserExecutor) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.responseParser = responseParser;
        this.categoryRepository = categoryRepository;
        this.offerRepository = offerRepository;
        this.trackedProductRepository = trackedProductRepository;
        this.monitoringJobRepository = monitoringJobRepository;
        this.wildberriesParserExecutor = wildberriesParserExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startDiscoveryOnApplicationReady() {
        if (!properties.isContinuousScanEnabled()) {
            log.info("Wildberries continuous discovery scan is disabled");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                runDiscoveryScan();
            } catch (RuntimeException e) {
                log.warn("Startup Wildberries discovery scan failed: {}", e.getMessage());
            }
        }, wildberriesParserExecutor);
    }

    @Scheduled(
            initialDelayString = "${wb.parser.scan-initial-delay-ms:900000}",
            fixedDelayString = "${wb.parser.scan-fixed-delay-ms:900000}"
    )
    public void scheduledDiscoveryScan() {
        if (!properties.isContinuousScanEnabled()) {
            return;
        }
        try {
            runDiscoveryScan();
        } catch (RuntimeException e) {
            log.warn("Scheduled Wildberries discovery scan failed: {}", e.getMessage());
        }
    }

    public WildberriesDiscoveryResult runDiscoveryScan() {
        if (!scanRunning.compareAndSet(false, true)) {
            log.info("Wildberries discovery scan is already running");
            return new WildberriesDiscoveryResult(
                    shouldUseBaseline(),
                    baselineReady.get(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        boolean baselineScan = shouldUseBaseline();
        try {
            String menuJson = httpClient.getJson(properties.getPromotionsUrl(), "https://www.wildberries.ru/");
            List<WildberriesCategoryRef> categories = responseParser.parsePromotionCategories(menuJson);
            saveCategoryTree(categories);
            List<WildberriesCategoryRef> categoriesToScan = limitCategories(categories.stream()
                    .filter(WildberriesCategoryRef::scannable)
                    .toList());
            log.info("Wildberries discovery scan started: baseline={}, categoriesLoaded={}, categoriesToScan={}",
                    baselineScan,
                    categories.size(),
                    categoriesToScan.size());

            List<CompletableFuture<CategoryScanResult>> futures = categoriesToScan.stream()
                    .map(category -> CompletableFuture
                            .supplyAsync(() -> scanCategory(category, baselineScan), wildberriesParserExecutor)
                            .exceptionally(error -> {
                                log.warn("WB category scan failed for {}: {}",
                                        category.categoryUrl(),
                                        error.getMessage());
                                return CategoryScanResult.failed();
                            }))
                    .toList();

            int pagesScanned = 0;
            int productsSeen = 0;
            int baselineProductsAdded = 0;
            int newProductsCreated = 0;
            int existingProductsSkipped = 0;
            int errors = 0;

            for (CompletableFuture<CategoryScanResult> future : futures) {
                CategoryScanResult result = future.join();
                pagesScanned += result.pagesScanned();
                productsSeen += result.productsSeen();
                baselineProductsAdded += result.baselineProductsAdded();
                newProductsCreated += result.newProductsCreated();
                existingProductsSkipped += result.existingProductsSkipped();
                errors += result.errors();
            }

            if (baselineScan) {
                baselineReady.set(true);
            }

            WildberriesDiscoveryResult result = new WildberriesDiscoveryResult(
                    baselineScan,
                    baselineReady.get(),
                    categories.size(),
                    categoriesToScan.size(),
                    pagesScanned,
                    productsSeen,
                    baselineProductsAdded,
                    newProductsCreated,
                    existingProductsSkipped,
                    errors
            );
            log.info("Wildberries discovery scan finished: {}", result);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Wildberries discovery scan failed: " + e.getMessage(), e);
        } finally {
            scanRunning.set(false);
        }
    }

    public void resetBaseline() {
        seenArticles.clear();
        baselineReady.set(false);
        log.info("Wildberries discovery baseline was reset");
    }

    public boolean isBaselineReady() {
        return baselineReady.get();
    }

    public int seenArticlesCount() {
        return seenArticles.size();
    }

    public boolean isScanRunning() {
        return scanRunning.get();
    }

    private CategoryScanResult scanCategory(WildberriesCategoryRef categoryRef, boolean baselineScan) {
        ProductCategory category = saveCategory(categoryRef);
        int pagesToRead = maxPagesToRead();
        int pagesScanned = 0;
        int productsSeen = 0;
        int baselineProductsAdded = 0;
        int newProductsCreated = 0;
        int existingProductsSkipped = 0;
        int errors = 0;

        for (int pageNumber = 1; pageNumber <= pagesToRead; pageNumber++) {
            try {
                String pageUrl = WildberriesCatalogUrlBuilder.build(categoryRef, pageNumber);
                WildberriesPage page = fetchCatalogPage(pageUrl, categoryRef.categoryUrl());
                if (page.products().isEmpty()) {
                    break;
                }

                if (pageNumber == 1 && page.total() > 0) {
                    int totalPages = (int) Math.ceil((double) page.total() / WB_PAGE_SIZE);
                    if (properties.getMaxPagesPerCategory() > 0) {
                        pagesToRead = Math.min(properties.getMaxPagesPerCategory(), Math.max(1, totalPages));
                    } else {
                        pagesToRead = Math.max(1, totalPages);
                    }
                }

                for (WildberriesParsedProduct product : page.products()) {
                    saveCurrentOffer(category, product);
                    ProductDiscoveryResult productResult = handleProduct(category, product, baselineScan);
                    productsSeen++;
                    baselineProductsAdded += productResult.baselineAdded();
                    newProductsCreated += productResult.created();
                    existingProductsSkipped += productResult.skipped();
                }

                pagesScanned++;
                delayBetweenRequests();
            } catch (IOException | RuntimeException e) {
                errors++;
                log.warn("WB category '{}' page {} failed: {}",
                        categoryRef.name(),
                        pageNumber,
                        e.getMessage());
                break;
            }
        }

        return new CategoryScanResult(
                pagesScanned,
                productsSeen,
                baselineProductsAdded,
                newProductsCreated,
                existingProductsSkipped,
                errors
        );
    }

    private ProductDiscoveryResult handleProduct(ProductCategory category,
                                                 WildberriesParsedProduct product,
                                                 boolean baselineScan) {
        if (baselineScan) {
            return seenArticles.add(product.article())
                    ? ProductDiscoveryResult.baselineProduct()
                    : ProductDiscoveryResult.skippedProduct();
        }

        if (!seenArticles.add(product.article())) {
            return ProductDiscoveryResult.skippedProduct();
        }
        if (trackedProductRepository.existsByMarketplaceArticle(product.article())) {
            return ProductDiscoveryResult.skippedProduct();
        }

        Instant discoveredAt = Instant.now();
        TrackedMarketplaceProduct trackedProduct = new TrackedMarketplaceProduct();
        trackedProduct.setCategory(category);
        trackedProduct.setMarketplaceArticle(product.article());
        trackedProduct.setProductName(product.name());
        trackedProduct.setSellerName(product.supplier());
        trackedProduct.setDiscoveredPrice(product.price());
        trackedProduct.setFeedbackReward(product.feedbackReward());
        trackedProduct.setBenefitPercent(product.benefitPercent());
        trackedProduct.setDiscoveredStock(product.stockQuantity());
        trackedProduct.setCardUrl(product.cardUrl());
        trackedProduct.setDetailUrl(WildberriesProductDetailUrlBuilder.build(product.article()));
        trackedProduct.setDiscoveredAt(discoveredAt);
        trackedProduct.setActive(true);

        MarketplaceMonitoringJob job = new MarketplaceMonitoringJob();
        job.setProduct(trackedProduct);
        job.setStage(0);
        job.setNextRunAt(discoveredAt.plus(properties.getProductMeasurementDelay()));
        job.setFinished(false);
        job.setCreatedAt(discoveredAt);
        job.setUpdatedAt(discoveredAt);

        try {
            TrackedMarketplaceProduct savedProduct = trackedProductRepository.save(trackedProduct);
            job.setProduct(savedProduct);
            monitoringJobRepository.save(job);
            log.info("New WB product discovered: article={}, nextMeasurementAt={}",
                    product.article(),
                    job.getNextRunAt());
            return ProductDiscoveryResult.createdProduct();
        } catch (DataIntegrityViolationException e) {
            log.info("WB product {} was already registered by another scan task", product.article());
            return ProductDiscoveryResult.skippedProduct();
        }
    }

    private void saveCurrentOffer(ProductCategory category, WildberriesParsedProduct product) {
        CompetitorOffer offer = offerRepository.findByMarketplaceArticle(product.article())
                .orElseGet(CompetitorOffer::new);
        offer.setCategory(category);
        offer.setCompetitorName(product.supplier());
        offer.setProductName(product.name());
        offer.setCompetitorPrice(product.price());
        offer.setFeedbackReward(product.feedbackReward());
        offer.setBenefitPercent(product.benefitPercent());
        offer.setStockQuantity(product.stockQuantity());
        offer.setMarketplaceArticle(product.article());
        offer.setCardUrl(product.cardUrl());
        offer.setCollectedAt(Instant.now());
        offerRepository.save(offer);
    }

    private WildberriesPage fetchCatalogPage(String pageUrl, String referer) throws IOException {
        String json = httpClient.getJson(pageUrl, referer);
        return responseParser.parseCatalogPage(json);
    }

    private ProductCategory saveCategory(WildberriesCategoryRef categoryRef) {
        ProductCategory category = categoryRepository.findByExternalUrl(categoryRef.categoryUrl())
                .orElseGet(ProductCategory::new);
        category.setName(categoryRef.name());
        category.setExternalUrl(categoryRef.categoryUrl());
        category.setWbShardKey(categoryRef.shardKey());
        category.setWbQuery(categoryRef.query());
        if (categoryRef.parentCategoryUrl() != null && !categoryRef.parentCategoryUrl().equals(categoryRef.categoryUrl())) {
            categoryRepository.findByExternalUrl(categoryRef.parentCategoryUrl())
                    .ifPresent(category::setParentCategory);
        }
        return categoryRepository.save(category);
    }

    private void saveCategoryTree(List<WildberriesCategoryRef> categories) {
        for (WildberriesCategoryRef category : categories) {
            saveCategory(category);
        }
    }

    private List<WildberriesCategoryRef> limitCategories(List<WildberriesCategoryRef> categories) {
        if (properties.getMaxCategories() <= 0 || properties.getMaxCategories() >= categories.size()) {
            return categories;
        }
        return categories.stream().limit(properties.getMaxCategories()).toList();
    }

    private int maxPagesToRead() {
        if (properties.getMaxPagesPerCategory() <= 0) {
            return Integer.MAX_VALUE;
        }
        return properties.getMaxPagesPerCategory();
    }

    private boolean shouldUseBaseline() {
        return properties.isBaselineOnStartup() && !baselineReady.get();
    }

    private void delayBetweenRequests() {
        if (properties.getRequestDelay().isZero() || properties.getRequestDelay().isNegative()) {
            return;
        }
        try {
            Thread.sleep(properties.getRequestDelay().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Wildberries discovery delay was interrupted");
        }
    }

    private record CategoryScanResult(
            int pagesScanned,
            int productsSeen,
            int baselineProductsAdded,
            int newProductsCreated,
            int existingProductsSkipped,
            int errors
    ) {
        static CategoryScanResult failed() {
            return new CategoryScanResult(0, 0, 0, 0, 0, 1);
        }
    }

    private record ProductDiscoveryResult(int baselineAdded, int created, int skipped) {
        static ProductDiscoveryResult baselineProduct() {
            return new ProductDiscoveryResult(1, 0, 0);
        }

        static ProductDiscoveryResult createdProduct() {
            return new ProductDiscoveryResult(0, 1, 0);
        }

        static ProductDiscoveryResult skippedProduct() {
            return new ProductDiscoveryResult(0, 0, 1);
        }
    }
}
