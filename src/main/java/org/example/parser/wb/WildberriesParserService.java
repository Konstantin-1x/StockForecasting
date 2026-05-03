package org.example.parser.wb;

import org.example.domain.CompetitorOffer;
import org.example.domain.ProductCategory;
import org.example.repository.CompetitorOfferRepository;
import org.example.repository.ProductCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Service
public class WildberriesParserService {

    private static final Logger log = LoggerFactory.getLogger(WildberriesParserService.class);
    private static final int WB_PAGE_SIZE = 100;

    private final WildberriesParserProperties properties;
    private final WildberriesHttpClient httpClient;
    private final WildberriesResponseParser responseParser;
    private final ProductCategoryRepository categoryRepository;
    private final CompetitorOfferRepository offerRepository;

    public WildberriesParserService(WildberriesParserProperties properties,
                                    WildberriesHttpClient httpClient,
                                    WildberriesResponseParser responseParser,
                                    ProductCategoryRepository categoryRepository,
                                    CompetitorOfferRepository offerRepository) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.responseParser = responseParser;
        this.categoryRepository = categoryRepository;
        this.offerRepository = offerRepository;
    }

    public WildberriesImportResult importRublesForReviews(Integer maxCategoriesOverride,
                                                          Integer maxPagesPerCategoryOverride) {
        int maxCategories = positiveOrDefault(maxCategoriesOverride, properties.getMaxCategories());
        int maxPagesPerCategory = positiveOrDefault(maxPagesPerCategoryOverride, properties.getMaxPagesPerCategory());
        log.info("Starting Wildberries import: maxCategories={}, maxPagesPerCategory={}",
                maxCategories,
                maxPagesPerCategory);

        try {
            String menuJson = httpClient.getJson(properties.getPromotionsUrl(), "https://www.wildberries.ru/");
            List<WildberriesCategoryRef> categories = responseParser.parsePromotionCategories(menuJson);
            log.info("Wildberries promotions menu parsed: {} candidate categories", categories.size());

            int categoriesProcessed = 0;
            int pagesProcessed = 0;
            int productsParsed = 0;
            int offersSaved = 0;

            for (WildberriesCategoryRef categoryRef : categories.stream().limit(maxCategories).toList()) {
                log.info("Processing WB category: {} ({})", categoryRef.name(), categoryRef.categoryUrl());
                ProductCategory category = saveCategory(categoryRef);
                int pagesToRead = maxPagesPerCategory;

                for (int pageNumber = 1; pageNumber <= pagesToRead; pageNumber++) {
                    String pageUrl = WildberriesCatalogUrlBuilder.build(categoryRef, pageNumber);
                    WildberriesPage page = fetchCatalogPage(pageUrl, categoryRef.categoryUrl());
                    if (page.products().isEmpty()) {
                        break;
                    }

                    if (pageNumber == 1 && page.total() > 0) {
                        int totalPages = (int) Math.ceil((double) page.total() / WB_PAGE_SIZE);
                        pagesToRead = Math.min(maxPagesPerCategory, Math.max(1, totalPages));
                    }

                    productsParsed += page.products().size();
                    int pageOffersSaved = saveOffers(category, page.products());
                    offersSaved += pageOffersSaved;
                    pagesProcessed++;
                    log.info("WB category '{}' page {}: parsed {} product(s), saved {} offer(s)",
                            categoryRef.name(),
                            pageNumber,
                            page.products().size(),
                            pageOffersSaved);
                    delayBetweenRequests();
                }

                categoriesProcessed++;
            }

            log.info("Wildberries import finished: categoriesTotal={}, categoriesProcessed={}, pagesProcessed={}, productsParsed={}, offersSaved={}",
                    categories.size(),
                    categoriesProcessed,
                    pagesProcessed,
                    productsParsed,
                    offersSaved);
            return new WildberriesImportResult(
                    categories.size(),
                    categoriesProcessed,
                    pagesProcessed,
                    productsParsed,
                    offersSaved
            );
        } catch (IOException e) {
            throw new IllegalStateException("Wildberries import failed: " + e.getMessage(), e);
        }
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
        return categoryRepository.save(category);
    }

    private int saveOffers(ProductCategory category, List<WildberriesParsedProduct> products) {
        int saved = 0;
        Instant collectedAt = Instant.now();
        for (WildberriesParsedProduct product : products) {
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
            offer.setCollectedAt(collectedAt);

            offerRepository.save(offer);
            saved++;
        }
        return saved;
    }

    private void delayBetweenRequests() {
        if (properties.getRequestDelay().isZero() || properties.getRequestDelay().isNegative()) {
            return;
        }
        try {
            Thread.sleep(properties.getRequestDelay().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Wildberries parser delay was interrupted");
        }
    }

    private static int positiveOrDefault(Integer value, int defaultValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return value;
    }
}
