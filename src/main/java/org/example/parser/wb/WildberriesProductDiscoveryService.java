package org.example.parser.wb;

import org.example.domain.CompetitorOffer;
import org.example.domain.MarketplaceProductSnapshot;
import org.example.domain.ProductCategory;
import org.example.domain.TrackedMarketplaceProduct;
import org.example.repository.CompetitorOfferRepository;
import org.example.repository.MarketplaceProductSnapshotRepository;
import org.example.repository.ProductCategoryRepository;
import org.example.repository.TrackedMarketplaceProductRepository;
import org.example.web.data.DataTransferState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    private final MarketplaceProductSnapshotRepository snapshotRepository;
    private final ExecutorService wildberriesParserExecutor;
    private final DataTransferState transferState;
    private final Set<String> seenArticles = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean baselineReady = new AtomicBoolean(false);
    private final AtomicBoolean scanRunning = new AtomicBoolean(false);

    public WildberriesProductDiscoveryService(WildberriesParserProperties properties,
                                              WildberriesHttpClient httpClient,
                                              WildberriesResponseParser responseParser,
                                              ProductCategoryRepository categoryRepository,
                                              CompetitorOfferRepository offerRepository,
                                              TrackedMarketplaceProductRepository trackedProductRepository,
                                              MarketplaceProductSnapshotRepository snapshotRepository,
                                              DataTransferState transferState,
                                              ExecutorService wildberriesParserExecutor) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.responseParser = responseParser;
        this.categoryRepository = categoryRepository;
        this.offerRepository = offerRepository;
        this.trackedProductRepository = trackedProductRepository;
        this.snapshotRepository = snapshotRepository;
        this.transferState = transferState;
        this.wildberriesParserExecutor = wildberriesParserExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startDiscoveryOnApplicationReady() {
        if (!properties.isContinuousScanEnabled()) {
            log.info("Wildberries continuous discovery scan is disabled");
            return;
        }
        if (!properties.isBaselineOnStartup()) {
            log.info("Wildberries startup discovery scan is disabled");
            return;
        }
        if (transferState.isTransferInProgress()) {
            log.info("Wildberries startup discovery scan skipped: data transfer is running");
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
            initialDelayString = "${wb.parser.scan-initial-delay-ms:3600000}",
            fixedDelayString = "${wb.parser.scan-fixed-delay-ms:3600000}"
    )
    public void scheduledDiscoveryScan() {
        if (!properties.isContinuousScanEnabled()) {
            return;
        }
        if (transferState.isTransferInProgress()) {
            log.info("Wildberries discovery scan skipped: data transfer is running");
            return;
        }
        try {
            runDiscoveryScan();
        } catch (RuntimeException e) {
            log.warn("Scheduled Wildberries discovery scan failed: {}", e.getMessage());
        }
    }

    public WildberriesDiscoveryResult runDiscoveryScan() {
        if (transferState.isTransferInProgress()) {
            log.info("Wildberries discovery scan skipped: data transfer is running");
            return skippedDiscoveryResult(shouldUseBaseline());
        }
        if (!scanRunning.compareAndSet(false, true)) {
            log.info("Wildberries discovery scan is already running");
            return skippedDiscoveryResult(shouldUseBaseline());
        }

        boolean baselineScan = shouldUseBaseline();
        Set<String> articlesSeenInScan = ConcurrentHashMap.newKeySet();
        try {
            String menuJson = httpClient.getJson(properties.getPromotionsUrl(), "https://www.wildberries.ru/");
            List<WildberriesCategoryRef> categories = responseParser.parsePromotionCategories(menuJson);
            saveCategoryTree(categories);
            List<WildberriesCategoryRef> categoriesToScan = limitCategories(categories.stream()
                    .filter(WildberriesCategoryRef::scannable)
                    .toList());
            log.info("Wildberries hourly catalog snapshot started: baseline={}, categoriesLoaded={}, categoriesToScan={}",
                    baselineScan,
                    categories.size(),
                    categoriesToScan.size());

            List<CompletableFuture<CategoryScanResult>> futures = categoriesToScan.stream()
                    .map(category -> CompletableFuture
                            .supplyAsync(() -> scanCategory(category, baselineScan, articlesSeenInScan), wildberriesParserExecutor)
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

            if (errors == 0 && !categoriesToScan.isEmpty() && isFullCatalogSnapshot()) {
                int closedProducts = closeProductsMissingFromCurrentSnapshot(articlesSeenInScan, Instant.now());
                existingProductsSkipped += closedProducts;
            } else if (errors > 0) {
                log.warn("WB hourly catalog snapshot had {} error(s); missing products were not closed in this run", errors);
            } else if (!isFullCatalogSnapshot()) {
                log.info("WB hourly catalog snapshot used scan limits; missing products were not closed in this run");
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
            log.info("Wildberries hourly catalog snapshot finished: {}", result);
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

    private CategoryScanResult scanCategory(WildberriesCategoryRef categoryRef,
                                            boolean baselineScan,
                                            Set<String> articlesSeenInScan) {
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
                    ProductDiscoveryResult productResult = handleProduct(category, product, baselineScan, articlesSeenInScan);
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
                                                 boolean baselineScan,
                                                 Set<String> articlesSeenInScan) {
        if (!articlesSeenInScan.add(product.article())) {
            return ProductDiscoveryResult.skippedProduct();
        }
        seenArticles.add(product.article());
        saveCurrentOffer(category, product);

        Optional<TrackedMarketplaceProduct> existing = trackedProductRepository.findByMarketplaceArticle(product.article());
        if (existing.isPresent()) {
            TrackedMarketplaceProduct trackedProduct = existing.get();
            if (!trackedProduct.isActive()) {
                return ProductDiscoveryResult.skippedProduct();
            }
            updateTrackedProduct(trackedProduct, category, product);
            TrackedMarketplaceProduct savedProduct = trackedProductRepository.save(trackedProduct);
            saveCatalogSnapshot(savedProduct, product, Instant.now());
            return baselineScan ? ProductDiscoveryResult.baselineProduct() : ProductDiscoveryResult.skippedProduct();
        }

        Instant discoveredAt = Instant.now();
        TrackedMarketplaceProduct trackedProduct = new TrackedMarketplaceProduct();
        trackedProduct.setDiscoveredAt(discoveredAt);
        trackedProduct.setActive(true);
        updateTrackedProduct(trackedProduct, category, product);

        try {
            TrackedMarketplaceProduct savedProduct = trackedProductRepository.save(trackedProduct);
            saveCatalogSnapshot(savedProduct, product, discoveredAt);
            log.info("New WB product registered from hourly catalog snapshot: article={}", product.article());
            return ProductDiscoveryResult.createdProduct();
        } catch (DataIntegrityViolationException e) {
            log.info("WB product {} was already registered by another scan task", product.article());
            return ProductDiscoveryResult.skippedProduct();
        }
    }

    private void updateTrackedProduct(TrackedMarketplaceProduct trackedProduct,
                                      ProductCategory category,
                                      WildberriesParsedProduct product) {
        trackedProduct.setCategory(category);
        trackedProduct.setMarketplaceArticle(product.article());
        trackedProduct.setProductName(product.name());
        trackedProduct.setSellerName(product.supplier());
        trackedProduct.setSupplierId(product.supplierId());
        trackedProduct.setDiscoveredPrice(product.price());
        trackedProduct.setFeedbackReward(product.feedbackReward());
        trackedProduct.setBenefitPercent(product.benefitPercent());
        trackedProduct.setDiscoveredStock(product.stockQuantity());
        trackedProduct.setCardUrl(product.cardUrl());
        trackedProduct.setDetailUrl(WildberriesProductDetailUrlBuilder.build(product.article()));
    }

    private void saveCatalogSnapshot(TrackedMarketplaceProduct savedProduct,
                                     WildberriesParsedProduct product,
                                     Instant collectedAt) {
        MarketplaceProductSnapshot snapshot = new MarketplaceProductSnapshot();
        snapshot.setProduct(savedProduct);
        snapshot.setCollectedAt(collectedAt);
        snapshot.setPrice(product.price());
        snapshot.setStockQuantity(product.stockQuantity());
        snapshot.setFeedbackReward(product.feedbackReward());
        snapshot.setBenefitPercent(product.benefitPercent());
        snapshot.setRating(product.rating());
        snapshot.setReviewsCount(product.reviewsCount());
        snapshot.setMeasurementSource("DISCOVERY_CATALOG");
        snapshotRepository.save(snapshot);
    }

    private int closeProductsMissingFromCurrentSnapshot(Set<String> articlesSeenInScan, Instant collectedAt) {
        int closedProducts = 0;
        for (TrackedMarketplaceProduct product : trackedProductRepository.findAllByActiveTrue()) {
            if (articlesSeenInScan.contains(product.getMarketplaceArticle())) {
                continue;
            }

            MarketplaceProductSnapshot snapshot = new MarketplaceProductSnapshot();
            snapshot.setProduct(product);
            snapshot.setCollectedAt(collectedAt);
            snapshot.setPrice(product.getDiscoveredPrice());
            snapshot.setStockQuantity(0);
            snapshot.setFeedbackReward(BigDecimal.ZERO);
            snapshot.setBenefitPercent(BigDecimal.ZERO);
            snapshot.setMeasurementSource("DISCOVERY_ABSENT");
            snapshotRepository.save(snapshot);

            product.setActive(false);
            product.setCompletedAt(collectedAt);
            product.setDiscoveredStock(0);
            trackedProductRepository.save(product);
            closedProducts++;
        }
        if (closedProducts > 0) {
            log.info("Closed {} WB product time-series absent from current hourly catalog snapshot", closedProducts);
        }
        return closedProducts;
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

    private boolean isFullCatalogSnapshot() {
        return properties.getMaxCategories() <= 0 && properties.getMaxPagesPerCategory() <= 0;
    }

    private boolean shouldUseBaseline() {
        return properties.isBaselineOnStartup() && !baselineReady.get();
    }

    private WildberriesDiscoveryResult skippedDiscoveryResult(boolean baselineScan) {
        return new WildberriesDiscoveryResult(
                baselineScan,
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
