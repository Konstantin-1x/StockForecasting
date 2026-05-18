package org.example.forecast;

import org.example.domain.MarketplaceProductSnapshot;
import org.example.domain.Product;
import org.example.domain.ProductCategory;
import org.example.domain.PromotionForecast;
import org.example.domain.Seller;
import org.example.domain.TrackedMarketplaceProduct;
import org.example.repository.MarketplaceProductSnapshotRepository;
import org.example.repository.ProductRepository;
import org.example.repository.PromotionForecastRepository;
import org.example.repository.SellerRepository;
import org.example.repository.TrackedMarketplaceProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NeuralPromotionForecastService {

    private static final Logger log = LoggerFactory.getLogger(NeuralPromotionForecastService.class);

    private static final String MODEL_NAME = "mlp-neural-promo-v2";
    private static final String SYNTHETIC_SELLER_NAME = "WB Neural Forecast";
    private static final int HISTORY_TIMESTEPS = 24;
    private static final int MIN_TRAINING_SAMPLES = 24;
    private static final int MIN_SELLER_TRAINING_SAMPLES = 2;
    private static final int MIN_PRODUCT_HISTORY = 6;
    private static final int MAX_TRAINING_SAMPLES = 2500;
    private static final double PROMOTION_PRICE_DROP_THRESHOLD = 0.97;
    private static final int HIDDEN_LAYER_1 = 40;
    private static final int HIDDEN_LAYER_2 = 20;
    private static final int TRAINING_EPOCHS = 180;
    private static final int BATCH_SIZE = 16;
    private static final double LEARNING_RATE = 0.01;
    private static final double L2_PENALTY = 0.0005;
    private static final long RANDOM_SEED = 42L;
    private static final int SELLER_FORECAST_CATEGORY_CANDIDATE_LIMIT = 160;
    private static final int SELLER_FORECAST_PARENT_CANDIDATE_LIMIT = 240;
    private static final int SELLER_FORECAST_PRICE_CANDIDATE_LIMIT = 320;
    private static final int SELLER_FORECAST_GLOBAL_CANDIDATE_LIMIT = 320;
    private static final int FORECAST_MODEL_LABEL_LIMIT = 80;
    private static final int DEFAULT_REVIEW_TARGET = 20;
    private static final double WB_REVIEW_COMMISSION_RATE = 0.20;
    private static final double WB_REVIEW_VAT_RATE = 0.22;
    private static final double WB_LATE_REVIEW_RESERVE_RATE = 0.10;
    private static final Set<String> REDUCED_REVIEW_RATE_CATEGORIES = Set.of(
            "одежда",
            "обувь",
            "аксессуары",
            "белье",
            "бельё",
            "головные уборы",
            "красота",
            "бижутерия",
            "ювелирные украшения",
            "канцелярские товары",
            "бытовая техника",
            "товары для животных",
            "товары для малышей",
            "хозяйственные товары",
            "рукоделие",
            "аксессуары для волос",
            "спортивная одежда",
            "техника для кухни",
            "посуда и инвентарь",
            "детское питание",
            "продукты"
    );

    private final MarketplaceProductSnapshotRepository snapshotRepository;
    private final TrackedMarketplaceProductRepository trackedProductRepository;
    private final ProductRepository productRepository;
    private final SellerRepository sellerRepository;
    private final PromotionForecastRepository forecastRepository;

    public NeuralPromotionForecastService(MarketplaceProductSnapshotRepository snapshotRepository,
                                          TrackedMarketplaceProductRepository trackedProductRepository,
                                          ProductRepository productRepository,
                                          SellerRepository sellerRepository,
                                          PromotionForecastRepository forecastRepository) {
        this.snapshotRepository = snapshotRepository;
        this.trackedProductRepository = trackedProductRepository;
        this.productRepository = productRepository;
        this.sellerRepository = sellerRepository;
        this.forecastRepository = forecastRepository;
    }

    @Transactional
    public NeuralForecastRunResult recalculateForecasts() {
        return recalculateForecastsForTargets(null);
    }

    @Transactional
    public NeuralForecastRunResult recalculateForecastsForMarketplaceArticles(Set<String> marketplaceArticles) {
        Set<String> normalizedArticles = marketplaceArticles == null ? Set.of() : marketplaceArticles.stream()
                .filter(article -> article != null && !article.isBlank())
                .map(article -> article.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (normalizedArticles.isEmpty()) {
            return new NeuralForecastRunResult(0, 0, 0, 0, "Нет товаров продавца для запуска прогнозирования.");
        }

        List<Product> sellerProducts = productRepository.findAll().stream()
                .filter(product -> product.getMarketplaceArticle() != null
                        && normalizedArticles.contains(product.getMarketplaceArticle().trim().toLowerCase(Locale.ROOT)))
                .toList();
        if (sellerProducts.isEmpty()) {
            return new NeuralForecastRunResult(0, 0, 0, 0, "Нет товаров продавца для запуска прогнозирования.");
        }
        return recalculateForecastsForSellerProducts(sellerProducts);
    }

    @Transactional
    public NeuralForecastRunResult recalculateForecastForSellerProduct(Long productId,
                                                                       BigDecimal promotionBudget,
                                                                       Seller seller) {
        if (seller == null || seller.getId() == null) {
            return new NeuralForecastRunResult(0, 0, 0, 0, "К аккаунту не привязан продавец.");
        }
        Product sellerProduct = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Товар для прогнозирования не найден."));
        if (sellerProduct.getSeller() == null || !seller.getId().equals(sellerProduct.getSeller().getId())) {
            throw new IllegalArgumentException("Этот товар не принадлежит текущему продавцу.");
        }
        if (promotionBudget == null || promotionBudget.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Укажите бюджет промо акции не меньше 1 рубля.");
        }

        List<TrackedMarketplaceProduct> trackedProducts = loadForecastCandidates(sellerProduct);
        if (trackedProducts.isEmpty()) {
            return new NeuralForecastRunResult(1, 0, 0, 1, "Нет отслеживаемых WB-товаров для обучения нейросети.");
        }

        Map<Long, List<MarketplaceProductSnapshot>> historyByProduct = loadHistoryBulk(trackedProducts);
        Map<String, Optional<TrainedModel>> trainedModels = new HashMap<>();
        Optional<TrainedModel> trainedModel = resolveSparseTrainedModelForTarget(
                sellerProduct,
                trackedProducts,
                historyByProduct,
                trainedModels
        );
        TrainedModel model = trainedModel.orElse(null);
        List<TrackedMarketplaceProduct> analogCandidates = model == null
                ? productsWithHistory(trackedProducts, historyByProduct)
                : model.scopeProducts();
        Optional<TrackedMarketplaceProduct> analogProduct = selectAnalogProduct(
                sellerProduct,
                analogCandidates,
                historyByProduct
        );
        if (analogProduct.isEmpty()) {
            return new NeuralForecastRunResult(1, 0, 0, 1,
                    "Пока нет ни одного временного ряда WB для расчета. Запустите сбор данных и повторите расчет.");
        }

        TrackedMarketplaceProduct analog = analogProduct.get();
        List<MarketplaceProductSnapshot> analogHistory = historyByProduct.get(analog.getId());
        double[] prediction = model == null
                ? buildFallbackPrediction(sellerProduct, analog, analogHistory)
                : model.targetNormalizer()
                .denormalize(model.model().predict(model.featureNormalizer().normalize(
                        buildFeatureVector(sellerProduct, analog, analogHistory)
                )));
        PromotionScenario scenario = buildPromotionScenario(sellerProduct, promotionBudget, prediction, analogHistory);

        PromotionForecast forecast = new PromotionForecast();
        forecast.setCalculatedAt(Instant.now());
        forecast.setProduct(sellerProduct);
        forecast.setTrackedProduct(analog);
        forecast.setPromotionCostForecast(scenario.reviewReward());
        forecast.setPromotionStartHoursForecast(0);
        forecast.setPromotionStockForecast(scenario.stockAtStart());
        forecast.setPromotionPurchaseForecast(scenario.plannedReviews());
        forecast.setSelloutDaysForecast(scenario.platformCampaignDays());
        forecast.setPredictedSelloutHours(scenario.collectionHours());
        forecast.setPromotionBudget(scenario.budget());
        forecast.setRecommendedDiscountAmount(null);
        forecast.setRecommendedDiscountPercent(null);
        forecast.setConfidenceInterval(model == null ? decimal(35.0, 2) : decimal(model.metrics().confidencePercent(), 2));
        forecast.setForecastModel(limitText(forecastModelLabel(model), FORECAST_MODEL_LABEL_LIMIT));
        forecast.setTrainingSampleCount(model == null ? 0 : model.trainingSampleCount());
        forecast.setValidationMae(model == null ? null : decimal(model.metrics().averageMae(), 4));
        forecastRepository.save(forecast);

        String message = String.format(
                Locale.US,
                "Прогноз готов: товар '%s', бюджет %s ₽, ставка за отзыв %s ₽, план отзывов %d, ожидаемый сбор %d д. Минимальный срок акции WB: %d д.",
                sellerProduct.getName(),
                moneyText(scenario.budget()),
                moneyText(scenario.reviewReward()),
                scenario.plannedReviews(),
                scenario.collectionDays(),
                scenario.platformCampaignDays()
        );
        return new NeuralForecastRunResult(1, model == null ? 0 : model.trainingSampleCount(), 1, 0, message);
    }

    private NeuralForecastRunResult recalculateForecastsForTargets(Set<String> targetArticles) {
        List<TrackedMarketplaceProduct> trackedProducts = trackedProductRepository.findAll();
        if (trackedProducts.isEmpty()) {
            return new NeuralForecastRunResult(0, 0, 0, 0, "Нет отслеживаемых товаров для обучения нейросети.");
        }

        List<TrackedMarketplaceProduct> targetProducts = targetArticles == null
                ? trackedProducts
                : trackedProducts.stream()
                .filter(product -> product.getMarketplaceArticle() != null
                        && targetArticles.contains(product.getMarketplaceArticle().trim().toLowerCase(Locale.ROOT)))
                .toList();
        if (targetProducts.isEmpty()) {
            return new NeuralForecastRunResult(0, 0, 0, 0,
                    "Для товаров продавца пока нет собранной истории Wildberries. Дождитесь почасового сбора данных по этим артикулам.");
        }

        Map<Long, List<MarketplaceProductSnapshot>> historyByProduct = loadHistory(trackedProducts);
        Map<String, Optional<TrainedModel>> trainedModels = new HashMap<>();

        int forecastsSaved = 0;
        int skippedProducts = 0;
        int totalTrainingSamplesUsed = 0;
        Instant calculatedAt = Instant.now();

        log.info("Neural forecast recalculation started: trackedProducts={}, historySeries={}",
                targetProducts.size(),
                historyByProduct.size());

        for (TrackedMarketplaceProduct trackedProduct : targetProducts) {
            List<MarketplaceProductSnapshot> history = historyByProduct.get(trackedProduct.getId());
            if (history == null || history.size() < MIN_PRODUCT_HISTORY) {
                skippedProducts++;
                continue;
            }

            Optional<TrainedModel> trainedModel = resolveTrainedModelForTarget(
                    trackedProduct,
                    trackedProducts,
                    historyByProduct,
                    trainedModels
            );
            if (trainedModel.isEmpty()) {
                skippedProducts++;
                continue;
            }

            TrainedModel model = trainedModel.get();
            double[] features = buildFeatureVector(trackedProduct, history);
            double[] prediction = model.targetNormalizer()
                    .denormalize(model.model().predict(model.featureNormalizer().normalize(features)));

            PromotionForecast forecast = new PromotionForecast();
            forecast.setCalculatedAt(calculatedAt);
            forecast.setProduct(resolveForecastProduct(trackedProduct));
            forecast.setTrackedProduct(trackedProduct);

            BigDecimal promotionPrice = decimal(prediction[0], 2);
            int hoursToPromotion = nonNegativeInteger(prediction[1]);
            int stockDuringPromotion = nonNegativeInteger(prediction[2]);
            int selloutDays = (int) Math.ceil(hoursToPromotion / 24.0);

            forecast.setPromotionCostForecast(promotionPrice);
            forecast.setPromotionStartHoursForecast(hoursToPromotion);
            forecast.setPromotionStockForecast(stockDuringPromotion);
            forecast.setPromotionPurchaseForecast(stockDuringPromotion);
            forecast.setSelloutDaysForecast(selloutDays);
            forecast.setConfidenceInterval(decimal(model.metrics().confidencePercent(), 2));
            forecast.setForecastModel(limitText(MODEL_NAME + " / " + model.scopeLabel(), FORECAST_MODEL_LABEL_LIMIT));
            forecast.setTrainingSampleCount(model.trainingSampleCount());
            forecast.setValidationMae(decimal(model.metrics().averageMae(), 4));

            forecastRepository.save(forecast);
            forecastsSaved++;
            totalTrainingSamplesUsed += model.trainingSampleCount();
        }

        String message = forecastsSaved == 0
                ? "Нейросеть не смогла построить прогнозы: не хватило исторических примеров с будущим началом акции."
                : String.format(
                Locale.US,
                "Нейросетевой прогноз готов: сохранено %d прогнозов, пропущено %d товаров, использовано %d обучающих примеров.",
                forecastsSaved,
                skippedProducts,
                totalTrainingSamplesUsed
        );
        log.info("Neural forecast recalculation finished: {}", message);
        return new NeuralForecastRunResult(
                targetProducts.size(),
                totalTrainingSamplesUsed,
                forecastsSaved,
                skippedProducts,
                message
        );
    }

    private NeuralForecastRunResult recalculateForecastsForSellerProducts(List<Product> sellerProducts) {
        List<TrackedMarketplaceProduct> trackedProducts = trackedProductRepository.findAll();
        if (trackedProducts.isEmpty()) {
            return new NeuralForecastRunResult(0, 0, 0, 0, "Нет отслеживаемых WB-товаров для обучения нейросети.");
        }

        Map<Long, List<MarketplaceProductSnapshot>> historyByProduct = loadHistory(trackedProducts);
        Map<String, Optional<TrainedModel>> trainedModels = new HashMap<>();

        int forecastsSaved = 0;
        int skippedProducts = 0;
        int totalTrainingSamplesUsed = 0;
        Instant calculatedAt = Instant.now();

        log.info("Seller neural forecast recalculation started: sellerProducts={}, historySeries={}",
                sellerProducts.size(),
                historyByProduct.size());

        for (Product sellerProduct : sellerProducts) {
            Optional<TrainedModel> trainedModel = resolveTrainedModelForTarget(
                    sellerProduct,
                    trackedProducts,
                    historyByProduct,
                    trainedModels
            );
            if (trainedModel.isEmpty()) {
                skippedProducts++;
                continue;
            }

            TrainedModel model = trainedModel.get();
            Optional<TrackedMarketplaceProduct> analogProduct = selectAnalogProduct(
                    sellerProduct,
                    model.scopeProducts(),
                    historyByProduct
            );
            if (analogProduct.isEmpty()) {
                skippedProducts++;
                continue;
            }

            TrackedMarketplaceProduct analog = analogProduct.get();
            double[] features = buildFeatureVector(
                    sellerProduct,
                    analog,
                    historyByProduct.get(analog.getId())
            );
            double[] prediction = model.targetNormalizer()
                    .denormalize(model.model().predict(model.featureNormalizer().normalize(features)));

            PromotionForecast forecast = new PromotionForecast();
            forecast.setCalculatedAt(calculatedAt);
            forecast.setProduct(sellerProduct);
            forecast.setTrackedProduct(analog);

            BigDecimal promotionPrice = decimal(prediction[0], 2);
            int hoursToPromotion = nonNegativeInteger(prediction[1]);
            int stockDuringPromotion = nonNegativeInteger(prediction[2]);
            int selloutDays = (int) Math.ceil(hoursToPromotion / 24.0);

            forecast.setPromotionCostForecast(promotionPrice);
            forecast.setPromotionStartHoursForecast(hoursToPromotion);
            forecast.setPromotionStockForecast(stockDuringPromotion);
            forecast.setPromotionPurchaseForecast(stockDuringPromotion);
            forecast.setSelloutDaysForecast(selloutDays);
            forecast.setConfidenceInterval(decimal(model.metrics().confidencePercent(), 2));
            forecast.setForecastModel(limitText(MODEL_NAME + " / " + model.scopeLabel(), FORECAST_MODEL_LABEL_LIMIT));
            forecast.setTrainingSampleCount(model.trainingSampleCount());
            forecast.setValidationMae(decimal(model.metrics().averageMae(), 4));

            forecastRepository.save(forecast);
            forecastsSaved++;
            totalTrainingSamplesUsed += model.trainingSampleCount();
        }

        String message = forecastsSaved == 0
                ? "Нейросеть не смогла построить прогнозы: в выбранных категориях пока не хватает исторических WB-примеров с началом акции."
                : String.format(
                Locale.US,
                "Нейросетевой прогноз по аналогам готов: сохранено %d прогнозов, пропущено %d товаров, использовано %d обучающих примеров.",
                forecastsSaved,
                skippedProducts,
                totalTrainingSamplesUsed
        );
        log.info("Seller neural forecast recalculation finished: {}", message);
        return new NeuralForecastRunResult(
                sellerProducts.size(),
                totalTrainingSamplesUsed,
                forecastsSaved,
                skippedProducts,
                message
        );
    }

    private Map<Long, List<MarketplaceProductSnapshot>> loadHistory(List<TrackedMarketplaceProduct> trackedProducts) {
        Map<Long, List<MarketplaceProductSnapshot>> historyByProduct = new HashMap<>();
        for (TrackedMarketplaceProduct trackedProduct : trackedProducts) {
            List<MarketplaceProductSnapshot> snapshots =
                    snapshotRepository.findByProductIdOrderByCollectedAtAsc(trackedProduct.getId());
            if (!snapshots.isEmpty()) {
                historyByProduct.put(trackedProduct.getId(), snapshots);
            }
        }
        return historyByProduct;
    }

    private List<TrackedMarketplaceProduct> loadForecastCandidates(Product target) {
        Map<Long, TrackedMarketplaceProduct> candidates = new LinkedHashMap<>();
        ProductCategory category = target.getCategory();
        if (category != null) {
            addCandidates(
                    candidates,
                    trackedProductRepository.findByCategoryOrderByDiscoveredAtDesc(
                            category,
                            PageRequest.of(0, SELLER_FORECAST_CATEGORY_CANDIDATE_LIMIT)
                    )
            );

            if (category.getParentCategory() != null) {
                addCandidates(
                        candidates,
                        trackedProductRepository.findByParentCategory(
                                category.getParentCategory(),
                                PageRequest.of(0, SELLER_FORECAST_PARENT_CANDIDATE_LIMIT)
                        )
                );
            }
        }

        addCandidates(candidates, loadSimilarPriceCandidates(target));
        addCandidates(
                candidates,
                trackedProductRepository.findAllByOrderByDiscoveredAtDesc(
                        PageRequest.of(0, SELLER_FORECAST_GLOBAL_CANDIDATE_LIMIT)
                )
        );
        return new ArrayList<>(candidates.values());
    }

    private List<TrackedMarketplaceProduct> loadSimilarPriceCandidates(Product target) {
        double basePrice = positiveDouble(target.getBasePrice());
        if (basePrice <= 0.0) {
            return List.of();
        }

        BigDecimal minPrice = decimal(Math.max(1.0, basePrice * 0.45), 2);
        BigDecimal maxPrice = decimal(basePrice * 1.75, 2);
        return trackedProductRepository.findByDiscoveredPriceBetweenOrderByDiscoveredAtDesc(
                minPrice,
                maxPrice,
                PageRequest.of(0, SELLER_FORECAST_PRICE_CANDIDATE_LIMIT)
        );
    }

    private static void addCandidates(Map<Long, TrackedMarketplaceProduct> target,
                                      List<TrackedMarketplaceProduct> source) {
        for (TrackedMarketplaceProduct product : source) {
            if (product.getId() != null) {
                target.putIfAbsent(product.getId(), product);
            }
        }
    }

    private Map<Long, List<MarketplaceProductSnapshot>> loadHistoryBulk(List<TrackedMarketplaceProduct> trackedProducts) {
        List<Long> productIds = trackedProducts.stream()
                .map(TrackedMarketplaceProduct::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<MarketplaceProductSnapshot>> historyByProduct = new LinkedHashMap<>();
        List<MarketplaceProductSnapshot> snapshots =
                snapshotRepository.findByProductIdsOrderByProductAndCollectedAt(productIds);
        for (MarketplaceProductSnapshot snapshot : snapshots) {
            if (snapshot.getProduct() == null || snapshot.getProduct().getId() == null) {
                continue;
            }
            historyByProduct.computeIfAbsent(snapshot.getProduct().getId(), ignored -> new ArrayList<>())
                    .add(snapshot);
        }
        return historyByProduct;
    }

    private Optional<TrainedModel> resolveTrainedModelForTarget(TrackedMarketplaceProduct target,
                                                               List<TrackedMarketplaceProduct> trackedProducts,
                                                               Map<Long, List<MarketplaceProductSnapshot>> historyByProduct,
                                                               Map<String, Optional<TrainedModel>> trainedModels) {
        for (TrainingScope scope : trainingScopesFor(target, trackedProducts, historyByProduct)) {
            Optional<TrainedModel> cached = trainedModels.get(scope.key());
            if (cached != null) {
                if (cached.isPresent()) {
                    return cached;
                }
                continue;
            }

            Optional<TrainedModel> trained = trainModel(scope, historyByProduct);
            trainedModels.put(scope.key(), trained);
            if (trained.isPresent()) {
                return trained;
            }
        }
        return Optional.empty();
    }

    private Optional<TrainedModel> resolveTrainedModelForTarget(Product target,
                                                               List<TrackedMarketplaceProduct> trackedProducts,
                                                               Map<Long, List<MarketplaceProductSnapshot>> historyByProduct,
                                                               Map<String, Optional<TrainedModel>> trainedModels) {
        for (TrainingScope scope : trainingScopesFor(target, trackedProducts, historyByProduct)) {
            Optional<TrainedModel> cached = trainedModels.get(scope.key());
            if (cached != null) {
                if (cached.isPresent()) {
                    return cached;
                }
                continue;
            }

            Optional<TrainedModel> trained = trainModel(scope, historyByProduct);
            trainedModels.put(scope.key(), trained);
            if (trained.isPresent()) {
                return trained;
            }
        }
        return Optional.empty();
    }

    private Optional<TrainedModel> resolveSparseTrainedModelForTarget(Product target,
                                                                     List<TrackedMarketplaceProduct> trackedProducts,
                                                                     Map<Long, List<MarketplaceProductSnapshot>> historyByProduct,
                                                                     Map<String, Optional<TrainedModel>> trainedModels) {
        for (TrainingScope scope : trainingScopesFor(target, trackedProducts, historyByProduct)) {
            String sparseKey = "seller-sparse:" + scope.key();
            Optional<TrainedModel> cached = trainedModels.get(sparseKey);
            if (cached != null) {
                if (cached.isPresent()) {
                    return cached;
                }
                continue;
            }

            Optional<TrainedModel> trained = trainModel(scope, historyByProduct, MIN_SELLER_TRAINING_SAMPLES);
            trainedModels.put(sparseKey, trained);
            if (trained.isPresent()) {
                return trained;
            }
        }
        return Optional.empty();
    }

    private List<TrainingScope> trainingScopesFor(TrackedMarketplaceProduct target,
                                                  List<TrackedMarketplaceProduct> trackedProducts,
                                                  Map<Long, List<MarketplaceProductSnapshot>> historyByProduct) {
        Map<String, TrainingScope> scopes = new LinkedHashMap<>();
        Long categoryId = categoryId(target);
        if (categoryId != null) {
            scopes.put("category:" + categoryId, new TrainingScope(
                    "category:" + categoryId,
                    "категория " + categoryName(target),
                    productsWithHistory(trackedProducts, historyByProduct).stream()
                            .filter(product -> categoryId.equals(categoryId(product)))
                            .toList()
            ));
        }

        Long parentCategoryId = parentCategoryId(target);
        if (parentCategoryId != null) {
            scopes.put("parent-category:" + parentCategoryId, new TrainingScope(
                    "parent-category:" + parentCategoryId,
                    "родительская категория " + parentCategoryId,
                    productsWithHistory(trackedProducts, historyByProduct).stream()
                            .filter(product -> parentCategoryId.equals(parentCategoryId(product)))
                            .toList()
            ));
        }

        scopes.put("all-products", new TrainingScope(
                "all-products",
                "все товары",
                productsWithHistory(trackedProducts, historyByProduct)
        ));
        return scopes.values().stream()
                .filter(scope -> !scope.products().isEmpty())
                .toList();
    }

    private List<TrainingScope> trainingScopesFor(Product target,
                                                  List<TrackedMarketplaceProduct> trackedProducts,
                                                  Map<Long, List<MarketplaceProductSnapshot>> historyByProduct) {
        List<TrackedMarketplaceProduct> productsWithHistory = productsWithHistory(trackedProducts, historyByProduct);
        Map<String, TrainingScope> scopes = new LinkedHashMap<>();
        Long categoryId = categoryId(target);
        if (categoryId != null) {
            scopes.put("category:" + categoryId, new TrainingScope(
                    "category:" + categoryId,
                    "категория " + categoryName(target),
                    productsWithHistory.stream()
                            .filter(product -> categoryId.equals(categoryId(product)))
                            .toList()
            ));
        }

        Long parentCategoryId = parentCategoryId(target);
        if (parentCategoryId != null) {
            scopes.put("parent-category:" + parentCategoryId, new TrainingScope(
                    "parent-category:" + parentCategoryId,
                    "родительская категория " + parentCategoryId,
                    productsWithHistory.stream()
                            .filter(product -> parentCategoryId.equals(parentCategoryId(product)))
                            .toList()
            ));
        }

        scopes.put("all-products", new TrainingScope(
                "all-products",
                "все товары",
                productsWithHistory
        ));
        return scopes.values().stream()
                .filter(scope -> !scope.products().isEmpty())
                .toList();
    }

    private Optional<TrainedModel> trainModel(TrainingScope scope,
                                             Map<Long, List<MarketplaceProductSnapshot>> historyByProduct) {
        return trainModel(scope, historyByProduct, MIN_TRAINING_SAMPLES);
    }

    private Optional<TrainedModel> trainModel(TrainingScope scope,
                                             Map<Long, List<MarketplaceProductSnapshot>> historyByProduct,
                                             int minTrainingSamples) {
        List<TrainingSample> trainingSamples = buildTrainingSamples(scope.products(), historyByProduct);
        if (trainingSamples.size() < minTrainingSamples) {
            log.info("Neural forecast scope skipped: scope={}, products={}, samples={}, minSamples={}",
                    scope.label(),
                    scope.products().size(),
                    trainingSamples.size(),
                    minTrainingSamples);
            return Optional.empty();
        }

        Dataset dataset = Dataset.from(trainingSamples);
        SplitDataset split = splitDataset(dataset, new Random(scopeSeed(scope.key())));
        Normalizer featureNormalizer = Normalizer.fit(split.trainingFeatures());
        Normalizer targetNormalizer = Normalizer.fit(split.trainingTargets());

        double[][] normalizedTrainFeatures = featureNormalizer.normalize(split.trainingFeatures());
        double[][] normalizedTrainTargets = targetNormalizer.normalize(split.trainingTargets());
        double[][] normalizedValidationFeatures = featureNormalizer.normalize(split.validationFeatures());

        MultiLayerPerceptronRegressor model = new MultiLayerPerceptronRegressor(
                dataset.featureSize(),
                HIDDEN_LAYER_1,
                HIDDEN_LAYER_2,
                dataset.targetSize(),
                new Random(scopeSeed(scope.key()))
        );

        log.info("Neural forecast training started: scope={}, products={}, samples={}, features={}",
                scope.label(),
                scope.products().size(),
                trainingSamples.size(),
                dataset.featureSize());

        model.fit(
                normalizedTrainFeatures,
                normalizedTrainTargets,
                TRAINING_EPOCHS,
                BATCH_SIZE,
                LEARNING_RATE,
                L2_PENALTY,
                new Random(scopeSeed(scope.key()) + 7)
        );

        Metrics validationMetrics = evaluate(
                model,
                normalizedValidationFeatures,
                split.validationTargets(),
                targetNormalizer
        );
        log.info("Neural forecast training finished: scope={}, samples={}, mae={}, confidence={}",
                scope.label(),
                trainingSamples.size(),
                validationMetrics.averageMae(),
                validationMetrics.confidencePercent());

        return Optional.of(new TrainedModel(
                model,
                featureNormalizer,
                targetNormalizer,
                validationMetrics,
                trainingSamples.size(),
                scope.label(),
                scope.products()
        ));
    }

    private List<TrainingSample> buildTrainingSamples(List<TrackedMarketplaceProduct> trackedProducts,
                                                      Map<Long, List<MarketplaceProductSnapshot>> historyByProduct) {
        List<TrainingSample> samples = new ArrayList<>();
        for (TrackedMarketplaceProduct trackedProduct : trackedProducts) {
            List<MarketplaceProductSnapshot> history = historyByProduct.get(trackedProduct.getId());
            if (history == null || history.size() < MIN_PRODUCT_HISTORY) {
                continue;
            }
            for (int currentIndex = MIN_PRODUCT_HISTORY - 1; currentIndex < history.size() - 1; currentIndex++) {
                PromotionTarget target = locateNextPromotion(history, currentIndex);
                if (target == null) {
                    continue;
                }
                List<MarketplaceProductSnapshot> accumulatedHistory = history.subList(0, currentIndex + 1);
                samples.add(new TrainingSample(
                        buildFeatureVector(trackedProduct, accumulatedHistory),
                        new double[]{
                                target.reviewReward(),
                                target.hoursUntilPromotion(),
                                target.promotionStock()
                        }
                ));
            }
        }
        return limitTrainingSamples(samples);
    }

    private static List<TrainingSample> limitTrainingSamples(List<TrainingSample> samples) {
        if (samples.size() <= MAX_TRAINING_SAMPLES) {
            return samples;
        }

        List<TrainingSample> limited = new ArrayList<>(MAX_TRAINING_SAMPLES);
        for (int index = 0; index < MAX_TRAINING_SAMPLES; index++) {
            int sourceIndex = (int) Math.floor((double) index * samples.size() / MAX_TRAINING_SAMPLES);
            limited.add(samples.get(sourceIndex));
        }
        return limited;
    }

    private PromotionTarget locateNextPromotion(List<MarketplaceProductSnapshot> history, int currentIndex) {
        MarketplaceProductSnapshot current = history.get(currentIndex);
        for (int nextIndex = currentIndex + 1; nextIndex < history.size(); nextIndex++) {
            MarketplaceProductSnapshot previous = history.get(nextIndex - 1);
            MarketplaceProductSnapshot candidate = history.get(nextIndex);
            if (!isPromotionStart(previous, candidate)) {
                continue;
            }
            long hours = Math.max(0, Duration.between(current.getCollectedAt(), candidate.getCollectedAt()).toHours());
            return new PromotionTarget(
                    rewardAmount(candidate),
                    hours,
                    Math.max(0, candidate.getStockQuantity() == null ? 0 : candidate.getStockQuantity())
            );
        }
        return null;
    }

    private boolean isPromotionStart(MarketplaceProductSnapshot previous, MarketplaceProductSnapshot candidate) {
        double previousReward = rewardAmount(previous);
        double candidateReward = rewardAmount(candidate);
        boolean rewardActivated = candidateReward > 0.0 && previousReward <= 0.0;
        boolean rewardIncreased = candidateReward > 0.0 && previousReward > 0.0 && candidateReward >= previousReward * 1.25;
        return rewardActivated || rewardIncreased;
    }

    private double[] buildFeatureVector(TrackedMarketplaceProduct trackedProduct,
                                        List<MarketplaceProductSnapshot> fullHistory) {
        return buildFeatureVectorFromHistory(
                fullHistory,
                1.0,
                1.0,
                positiveDouble(trackedProduct.getDiscoveredPrice()),
                trackedProduct.getDiscoveredStock() == null ? 0.0 : trackedProduct.getDiscoveredStock(),
                categoryId(trackedProduct)
        );
    }

    private double[] buildFeatureVector(Product targetProduct,
                                        TrackedMarketplaceProduct analogProduct,
                                        List<MarketplaceProductSnapshot> analogHistory) {
        MarketplaceProductSnapshot latest = analogHistory.get(analogHistory.size() - 1);
        double targetPrice = positiveDouble(targetProduct.getBasePrice());
        double targetStock = targetProduct.getCurrentStock() == null ? 0.0 : targetProduct.getCurrentStock();
        double latestAnalogPrice = positiveDouble(latest.getPrice());
        double latestAnalogStock = latest.getStockQuantity() == null ? 0.0 : latest.getStockQuantity();
        double priceScale = targetPrice > 0.0 && latestAnalogPrice > 0.0 ? targetPrice / latestAnalogPrice : 1.0;
        double stockScale = targetStock > 0.0 && latestAnalogStock > 0.0 ? targetStock / latestAnalogStock : 1.0;

        return buildFeatureVectorFromHistory(
                analogHistory,
                priceScale,
                stockScale,
                targetPrice > 0.0 ? targetPrice : positiveDouble(analogProduct.getDiscoveredPrice()),
                targetStock > 0.0 ? targetStock : (analogProduct.getDiscoveredStock() == null ? 0.0 : analogProduct.getDiscoveredStock()),
                categoryId(targetProduct)
        );
    }

    private double[] buildFeatureVectorFromHistory(List<MarketplaceProductSnapshot> fullHistory,
                                                   double priceScale,
                                                   double stockScale,
                                                   double discoveredPrice,
                                                   double discoveredStock,
                                                   Long categoryId) {
        List<MarketplaceProductSnapshot> sampledHistory = sampleHistory(fullHistory, HISTORY_TIMESTEPS);
        List<Double> features = new ArrayList<>(HISTORY_TIMESTEPS * 8 + 10);
        MarketplaceProductSnapshot latest = fullHistory.get(fullHistory.size() - 1);
        double minPrice = Double.POSITIVE_INFINITY;
        double maxPrice = 0.0;
        double sumPrice = 0.0;
        double sumStock = 0.0;
        double sumReward = 0.0;
        double sumBenefit = 0.0;

        for (MarketplaceProductSnapshot snapshot : fullHistory) {
            double price = positiveDouble(snapshot.getPrice()) * priceScale;
            double stock = (snapshot.getStockQuantity() == null ? 0.0 : snapshot.getStockQuantity()) * stockScale;
            double reward = positiveDouble(snapshot.getFeedbackReward());
            double benefit = positiveDouble(snapshot.getBenefitPercent());
            minPrice = Math.min(minPrice, price);
            maxPrice = Math.max(maxPrice, price);
            sumPrice += price;
            sumStock += stock;
            sumReward += reward;
            sumBenefit += benefit;
        }

        for (MarketplaceProductSnapshot snapshot : sampledHistory) {
            double price = positiveDouble(snapshot.getPrice()) * priceScale;
            double stock = (snapshot.getStockQuantity() == null ? 0.0 : snapshot.getStockQuantity()) * stockScale;
            double reward = positiveDouble(snapshot.getFeedbackReward());
            double benefit = positiveDouble(snapshot.getBenefitPercent());
            double rating = positiveDouble(snapshot.getRating());
            double reviews = snapshot.getReviewsCount() == null ? 0.0 : snapshot.getReviewsCount();
            double hoursFromLatest = Math.max(0, Duration.between(snapshot.getCollectedAt(), latest.getCollectedAt()).toHours());

            features.add(price);
            features.add(stock);
            features.add(reward);
            features.add(benefit);
            features.add(rating);
            features.add(reviews);
            features.add(hoursFromLatest);
            features.add("PRODUCT_DETAIL".equals(snapshot.getMeasurementSource()) ? 1.0 : 0.0);
        }

        double averagePrice = sumPrice / fullHistory.size();
        double averageStock = sumStock / fullHistory.size();
        double averageReward = sumReward / fullHistory.size();
        double averageBenefit = sumBenefit / fullHistory.size();
        double currentPrice = positiveDouble(latest.getPrice()) * priceScale;
        double currentStock = (latest.getStockQuantity() == null ? 0.0 : latest.getStockQuantity()) * stockScale;
        MarketplaceProductSnapshot oldest = fullHistory.get(0);
        double oldestPrice = positiveDouble(oldest.getPrice()) * priceScale;
        double oldestStock = (oldest.getStockQuantity() == null ? 0.0 : oldest.getStockQuantity()) * stockScale;
        double priceRange = minPrice == Double.POSITIVE_INFINITY ? 0.0 : maxPrice - minPrice;

        features.add(averagePrice);
        features.add(averageStock);
        features.add(averageReward);
        features.add(averageBenefit);
        features.add(currentPrice - oldestPrice);
        features.add(currentStock - oldestStock);
        features.add(priceRange);
        features.add(discoveredPrice);
        features.add(discoveredStock);
        features.add(categoryId == null ? 0.0 : categoryId);

        return features.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private Optional<TrackedMarketplaceProduct> selectAnalogProduct(Product target,
                                                                    List<TrackedMarketplaceProduct> candidates,
                                                                    Map<Long, List<MarketplaceProductSnapshot>> historyByProduct) {
        return candidates.stream()
                .filter(candidate -> historyByProduct.containsKey(candidate.getId()))
                .min(Comparator.comparingDouble(candidate -> similarityScore(target, candidate, historyByProduct.get(candidate.getId()))));
    }

    private double similarityScore(Product target,
                                   TrackedMarketplaceProduct candidate,
                                   List<MarketplaceProductSnapshot> history) {
        MarketplaceProductSnapshot latest = history.get(history.size() - 1);
        double targetPrice = positiveDouble(target.getBasePrice());
        double candidatePrice = positiveDouble(latest.getPrice());
        if (candidatePrice <= 0.0) {
            candidatePrice = positiveDouble(candidate.getDiscoveredPrice());
        }

        double targetStock = target.getCurrentStock() == null ? 0.0 : target.getCurrentStock();
        double candidateStock = latest.getStockQuantity() == null ? 0.0 : latest.getStockQuantity();
        if (candidateStock <= 0.0 && candidate.getDiscoveredStock() != null) {
            candidateStock = candidate.getDiscoveredStock();
        }

        double priceScore = targetPrice > 0.0 && candidatePrice > 0.0
                ? Math.abs(Math.log((targetPrice + 1.0) / (candidatePrice + 1.0)))
                : 0.5;
        double stockScore = targetStock > 0.0 && candidateStock > 0.0
                ? Math.abs(Math.log((targetStock + 1.0) / (candidateStock + 1.0)))
                : 0.2;
        double nameScore = 1.0 - tokenSimilarity(target.getName(), candidate.getProductName());
        double categoryPenalty = categoryId(target) != null && categoryId(target).equals(categoryId(candidate)) ? 0.0 : 0.4;

        return priceScore + stockScore * 0.35 + nameScore * 0.75 + categoryPenalty;
    }

    private static double tokenSimilarity(String first, String second) {
        Set<String> firstTokens = tokens(first);
        Set<String> secondTokens = tokens(second);
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(firstTokens);
        intersection.retainAll(secondTokens);
        Set<String> union = new HashSet<>(firstTokens);
        union.addAll(secondTokens);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> tokens(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return List.of(value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+")).stream()
                .filter(token -> token.length() >= 3)
                .collect(Collectors.toSet());
    }

    private double[] buildFallbackPrediction(Product sellerProduct,
                                             TrackedMarketplaceProduct analog,
                                             List<MarketplaceProductSnapshot> analogHistory) {
        MarketplaceProductSnapshot latest = analogHistory.get(analogHistory.size() - 1);
        double basePrice = positiveDouble(sellerProduct.getBasePrice());
        double analogPrice = positiveDouble(latest.getPrice());
        if (analogPrice <= 0.0) {
            analogPrice = positiveDouble(analog.getDiscoveredPrice());
        }

        double fallbackReward = estimateRewardFromHistory(analogHistory);
        if (fallbackReward <= 0.0) {
            fallbackReward = reviewRateBounds(sellerProduct, Math.max(basePrice, analogPrice)).minRate();
        }

        int sellerStock = sellerProduct.getCurrentStock() == null ? 0 : sellerProduct.getCurrentStock();
        int analogStock = latest.getStockQuantity() == null ? 0 : latest.getStockQuantity();
        if (analogStock <= 0 && analog.getDiscoveredStock() != null) {
            analogStock = analog.getDiscoveredStock();
        }
        int stock = Math.max(1, sellerStock > 0 ? sellerStock : analogStock);
        return new double[]{fallbackReward, 24.0 * 21.0, stock};
    }

    private String forecastModelLabel(TrainedModel model) {
        if (model == null) {
            return MODEL_NAME + " / похожие по цене товары / cold start";
        }
        return MODEL_NAME + " / " + model.scopeLabel() + " / бюджетный сценарий";
    }

    private PromotionScenario buildPromotionScenario(Product sellerProduct,
                                                     BigDecimal promotionBudget,
                                                     double[] prediction,
                                                     List<MarketplaceProductSnapshot> analogHistory) {
        double basePrice = positiveDouble(sellerProduct.getBasePrice());
        if (basePrice <= 0.0) {
            basePrice = Math.max(1.0, averagePositivePrice(analogHistory));
        }

        int currentStock = sellerProduct.getCurrentStock() == null ? 0 : sellerProduct.getCurrentStock();
        int modelStock = nonNegativeInteger(prediction[2]);
        int stockAtStart = Math.max(1, currentStock > 0 ? currentStock : modelStock);
        double budget = Math.max(1.0, positiveDouble(promotionBudget));

        ReviewRateBounds bounds = reviewRateBounds(sellerProduct, basePrice);
        double costMultiplier = reviewCostMultiplier();
        double modelReward = prediction[0] > 0.0 ? prediction[0] : estimateRewardFromHistory(analogHistory);

        double dailySales = estimateDailySales(analogHistory);
        if (dailySales <= 0.0) {
            int modelHours = Math.max(24, nonNegativeInteger(prediction[1]));
            dailySales = Math.max(1.0, stockAtStart / Math.max(1.0, modelHours / 24.0 + 3.0));
        }

        ReviewPlan plan = optimizeReviewPlan(
                budget,
                stockAtStart,
                basePrice,
                modelReward,
                dailySales,
                bounds,
                costMultiplier
        );

        return new PromotionScenario(
                money(plan.reviewReward()),
                stockAtStart,
                plan.plannedReviews(),
                plan.collectionDays(),
                plan.collectionDays() * 24,
                Math.max(21, plan.collectionDays()),
                money(budget)
        );
    }

    private ReviewPlan optimizeReviewPlan(double budget,
                                          int stockAtStart,
                                          double basePrice,
                                          double modelReward,
                                          double dailySales,
                                          ReviewRateBounds bounds,
                                          double costMultiplier) {
        int maxReviews = Math.max(1, stockAtStart);
        ReviewPlan bestPlan = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        List<Double> candidateRewards = reviewRewardCandidates(bounds, modelReward);
        for (double candidateReward : candidateRewards) {
            int plannedReviews = (int) Math.floor(budget / Math.max(1.0, candidateReward * costMultiplier));
            plannedReviews = Math.min(maxReviews, plannedReviews);
            if (plannedReviews <= 0) {
                continue;
            }

            double conversion = reviewConversion(candidateReward, basePrice);
            double expectedReviewsPerDay = Math.max(0.2, dailySales * conversion);
            int collectionDays = (int) Math.ceil(plannedReviews / expectedReviewsPerDay);
            collectionDays = Math.max(1, Math.min(90, collectionDays));

            double score = plannedReviews * conversion - collectionDays * 0.002;
            if (bestPlan == null
                    || score > bestScore
                    || (Math.abs(score - bestScore) < 1.0e-9
                    && candidateReward > bestPlan.reviewReward())) {
                bestScore = score;
                bestPlan = new ReviewPlan(candidateReward, plannedReviews, collectionDays);
            }
        }

        if (bestPlan != null) {
            return bestPlan;
        }

        double fallbackReward = Math.max(1.0, Math.min(bounds.minRate(), budget / costMultiplier));
        return new ReviewPlan(fallbackReward, 1, 90);
    }

    private List<Double> reviewRewardCandidates(ReviewRateBounds bounds, double modelReward) {
        List<Double> candidates = new ArrayList<>();
        double minRate = Math.max(1.0, bounds.minRate());
        double maxRate = Math.max(minRate, bounds.maxRate());
        for (double reward = minRate; reward <= maxRate; reward += 10.0) {
            candidates.add(reward);
        }
        candidates.add(maxRate);
        if (modelReward > 0.0) {
            candidates.add(clamp(modelReward, minRate, maxRate));
            candidates.add(clamp(modelReward * 0.75, minRate, maxRate));
            candidates.add(clamp(modelReward * 1.25, minRate, maxRate));
        }
        return candidates.stream()
                .map(value -> (double) Math.max(1, Math.round(value)))
                .distinct()
                .sorted()
                .toList();
    }

    private double reviewConversion(double reviewReward, double basePrice) {
        return clamp(0.04 + reviewReward / Math.max(1.0, basePrice) * 0.25, 0.03, 0.35);
    }

    private double estimateDailySales(List<MarketplaceProductSnapshot> history) {
        if (history == null || history.size() < 2) {
            return 0.0;
        }

        double stockDrop = 0.0;
        double days = 0.0;
        for (int index = 1; index < history.size(); index++) {
            MarketplaceProductSnapshot previous = history.get(index - 1);
            MarketplaceProductSnapshot current = history.get(index);
            if (previous.getStockQuantity() == null || current.getStockQuantity() == null) {
                continue;
            }
            int drop = previous.getStockQuantity() - current.getStockQuantity();
            long hours = Math.max(0, Duration.between(previous.getCollectedAt(), current.getCollectedAt()).toHours());
            if (drop <= 0 || hours <= 0) {
                continue;
            }
            stockDrop += drop;
            days += hours / 24.0;
        }
        return days <= 0.0 ? 0.0 : stockDrop / days;
    }

    private double estimateRewardFromHistory(List<MarketplaceProductSnapshot> history) {
        if (history == null || history.isEmpty()) {
            return 0.0;
        }

        double rewardSum = 0.0;
        int rewards = 0;
        for (MarketplaceProductSnapshot snapshot : history) {
            double reward = rewardAmount(snapshot);
            if (reward <= 0.0) {
                continue;
            }
            rewardSum += reward;
            rewards++;
        }
        return rewards == 0 ? 0.0 : rewardSum / rewards;
    }

    private double averagePositivePrice(List<MarketplaceProductSnapshot> history) {
        if (history == null || history.isEmpty()) {
            return 0.0;
        }

        double priceSum = 0.0;
        int prices = 0;
        for (MarketplaceProductSnapshot snapshot : history) {
            double price = positiveDouble(snapshot.getPrice());
            if (price <= 0.0) {
                continue;
            }
            priceSum += price;
            prices++;
        }
        return prices == 0 ? 0.0 : priceSum / prices;
    }

    private double rewardAmount(MarketplaceProductSnapshot snapshot) {
        double reward = positiveDouble(snapshot.getFeedbackReward());
        if (reward > 0.0) {
            return reward;
        }

        double price = positiveDouble(snapshot.getPrice());
        double benefit = positiveDouble(snapshot.getBenefitPercent());
        if (price <= 0.0 || benefit <= 0.0) {
            return 0.0;
        }
        return price * benefit / 100.0;
    }

    private ReviewRateBounds reviewRateBounds(Product product, double productPrice) {
        boolean reducedRate = hasReducedReviewRate(product.getCategory());
        double maxRate;
        if (productPrice <= 500.0) {
            maxRate = 500.0;
        } else if (productPrice <= 2000.0) {
            maxRate = 3000.0;
        } else {
            maxRate = 5000.0;
        }

        double minRate;
        if (reducedRate) {
            if (productPrice <= 500.0) {
                minRate = 30.0;
            } else if (productPrice <= 2000.0) {
                minRate = 40.0;
            } else {
                minRate = 100.0;
            }
        } else {
            minRate = 100.0;
        }
        return new ReviewRateBounds(minRate, maxRate);
    }

    private boolean hasReducedReviewRate(ProductCategory category) {
        ProductCategory current = category;
        while (current != null) {
            String categoryName = current.getName();
            if (categoryName != null) {
                String normalizedName = categoryName.trim().toLowerCase(Locale.ROOT);
                for (String reducedCategory : REDUCED_REVIEW_RATE_CATEGORIES) {
                    if (normalizedName.equals(reducedCategory) || normalizedName.contains(reducedCategory)) {
                        return true;
                    }
                }
            }
            current = current.getParentCategory();
        }
        return false;
    }

    private double reviewCostMultiplier() {
        return (1.0 + WB_REVIEW_COMMISSION_RATE)
                * (1.0 + WB_REVIEW_VAT_RATE)
                * (1.0 + WB_LATE_REVIEW_RESERVE_RATE);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String limitText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private List<MarketplaceProductSnapshot> sampleHistory(List<MarketplaceProductSnapshot> history, int points) {
        if (history.size() >= points) {
            List<MarketplaceProductSnapshot> sampled = new ArrayList<>(points);
            for (int index = 0; index < points; index++) {
                int sourceIndex = (int) Math.round((double) index * (history.size() - 1) / (points - 1));
                sampled.add(history.get(sourceIndex));
            }
            return sampled;
        }

        List<MarketplaceProductSnapshot> padded = new ArrayList<>(points);
        MarketplaceProductSnapshot first = history.get(0);
        int missing = points - history.size();
        for (int index = 0; index < missing; index++) {
            padded.add(first);
        }
        padded.addAll(history);
        return padded;
    }

    private SplitDataset splitDataset(Dataset dataset, Random random) {
        List<Integer> indices = new ArrayList<>(dataset.sampleCount());
        for (int index = 0; index < dataset.sampleCount(); index++) {
            indices.add(index);
        }
        Collections.shuffle(indices, random);

        int validationCount = Math.max(1, dataset.sampleCount() / 5);
        int trainingCount = dataset.sampleCount() - validationCount;
        if (trainingCount <= 0) {
            trainingCount = dataset.sampleCount() - 1;
            validationCount = 1;
        }

        double[][] trainingFeatures = new double[trainingCount][];
        double[][] trainingTargets = new double[trainingCount][];
        double[][] validationFeatures = new double[validationCount][];
        double[][] validationTargets = new double[validationCount][];

        for (int index = 0; index < indices.size(); index++) {
            int datasetIndex = indices.get(index);
            if (index < trainingCount) {
                trainingFeatures[index] = dataset.features()[datasetIndex];
                trainingTargets[index] = dataset.targets()[datasetIndex];
            } else {
                int validationIndex = index - trainingCount;
                validationFeatures[validationIndex] = dataset.features()[datasetIndex];
                validationTargets[validationIndex] = dataset.targets()[datasetIndex];
            }
        }
        return new SplitDataset(trainingFeatures, trainingTargets, validationFeatures, validationTargets);
    }

    private Metrics evaluate(MultiLayerPerceptronRegressor model,
                             double[][] normalizedValidationFeatures,
                             double[][] validationTargets,
                             Normalizer targetNormalizer) {
        if (validationTargets.length == 0) {
            return new Metrics(0.0, 50.0);
        }
        double absoluteErrorSum = 0.0;
        double targetMagnitudeSum = 0.0;
        int values = 0;
        for (int index = 0; index < validationTargets.length; index++) {
            double[] predicted = targetNormalizer.denormalize(model.predict(normalizedValidationFeatures[index]));
            double[] actual = validationTargets[index];
            for (int targetIndex = 0; targetIndex < actual.length; targetIndex++) {
                absoluteErrorSum += Math.abs(predicted[targetIndex] - actual[targetIndex]);
                targetMagnitudeSum += Math.max(1.0, Math.abs(actual[targetIndex]));
                values++;
            }
        }
        double averageMae = absoluteErrorSum / Math.max(1, values);
        double relativeError = absoluteErrorSum / Math.max(1.0, targetMagnitudeSum);
        double confidence = Math.max(5.0, 100.0 - relativeError * 100.0);
        return new Metrics(averageMae, Math.min(99.0, confidence));
    }

    private Product resolveForecastProduct(TrackedMarketplaceProduct trackedProduct) {
        Optional<Product> existing = productRepository.findByMarketplaceArticle(trackedProduct.getMarketplaceArticle());
        if (existing.isPresent()) {
            Product product = existing.get();
            product.setCategory(trackedProduct.getCategory());
            product.setName(trackedProduct.getProductName());
            product.setBasePrice(trackedProduct.getDiscoveredPrice());
            product.setCurrentStock(trackedProduct.getDiscoveredStock());
            return productRepository.save(product);
        }

        Product product = new Product();
        product.setSeller(resolveSyntheticSeller());
        product.setCategory(trackedProduct.getCategory());
        product.setMarketplaceArticle(trackedProduct.getMarketplaceArticle());
        product.setName(trackedProduct.getProductName());
        product.setDescription("Auto-generated from tracked marketplace history for neural forecast.");
        product.setBasePrice(trackedProduct.getDiscoveredPrice());
        product.setCurrentStock(trackedProduct.getDiscoveredStock());
        return productRepository.save(product);
    }

    private Seller resolveSyntheticSeller() {
        return sellerRepository.findByShopName(SYNTHETIC_SELLER_NAME)
                .orElseGet(() -> {
                    Seller seller = new Seller();
                    seller.setShopName(SYNTHETIC_SELLER_NAME);
                    seller.setRegistrationDate(LocalDate.now());
                    return sellerRepository.save(seller);
                });
    }

    private List<TrackedMarketplaceProduct> productsWithHistory(List<TrackedMarketplaceProduct> products,
                                                                Map<Long, List<MarketplaceProductSnapshot>> historyByProduct) {
        return products.stream()
                .filter(product -> {
                    List<MarketplaceProductSnapshot> history = historyByProduct.get(product.getId());
                    return history != null && history.size() >= MIN_PRODUCT_HISTORY;
                })
                .toList();
    }

    private static Long categoryId(TrackedMarketplaceProduct product) {
        ProductCategory category = product.getCategory();
        return category == null ? null : category.getId();
    }

    private static Long categoryId(Product product) {
        ProductCategory category = product.getCategory();
        return category == null ? null : category.getId();
    }

    private static Long parentCategoryId(TrackedMarketplaceProduct product) {
        ProductCategory category = product.getCategory();
        if (category == null || category.getParentCategory() == null) {
            return null;
        }
        return category.getParentCategory().getId();
    }

    private static Long parentCategoryId(Product product) {
        ProductCategory category = product.getCategory();
        if (category == null || category.getParentCategory() == null) {
            return null;
        }
        return category.getParentCategory().getId();
    }

    private static String categoryName(TrackedMarketplaceProduct product) {
        ProductCategory category = product.getCategory();
        if (category == null || category.getName() == null || category.getName().isBlank()) {
            return "без категории";
        }
        return category.getName();
    }

    private static String categoryName(Product product) {
        ProductCategory category = product.getCategory();
        if (category == null || category.getName() == null || category.getName().isBlank()) {
            return "без категории";
        }
        return category.getName();
    }

    private static long scopeSeed(String scopeKey) {
        return RANDOM_SEED + scopeKey.hashCode();
    }

    private static double positiveDouble(BigDecimal value) {
        return value == null ? 0.0 : Math.max(0.0, value.doubleValue());
    }

    private static BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(double value) {
        return decimal(value, 0);
    }

    private static String moneyText(BigDecimal value) {
        return value == null ? "0" : value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static int nonNegativeInteger(double value) {
        return (int) Math.max(0, Math.round(value));
    }

    private record TrainingScope(String key, String label, List<TrackedMarketplaceProduct> products) {
    }

    private record TrainedModel(MultiLayerPerceptronRegressor model,
                                Normalizer featureNormalizer,
                                Normalizer targetNormalizer,
                                Metrics metrics,
                                int trainingSampleCount,
                                String scopeLabel,
                                List<TrackedMarketplaceProduct> scopeProducts) {
    }

    private record TrainingSample(double[] features, double[] targets) {
    }

    private record PromotionTarget(double reviewReward, long hoursUntilPromotion, int promotionStock) {
    }

    private record PromotionScenario(BigDecimal reviewReward,
                                     int stockAtStart,
                                     int plannedReviews,
                                     int collectionDays,
                                     int collectionHours,
                                     int platformCampaignDays,
                                     BigDecimal budget) {
    }

    private record ReviewRateBounds(double minRate, double maxRate) {
    }

    private record ReviewPlan(double reviewReward, int plannedReviews, int collectionDays) {
    }

    private record Dataset(double[][] features, double[][] targets) {
        static Dataset from(List<TrainingSample> samples) {
            double[][] features = new double[samples.size()][];
            double[][] targets = new double[samples.size()][];
            for (int index = 0; index < samples.size(); index++) {
                features[index] = samples.get(index).features();
                targets[index] = samples.get(index).targets();
            }
            return new Dataset(features, targets);
        }

        int featureSize() {
            return features.length == 0 ? 0 : features[0].length;
        }

        int targetSize() {
            return targets.length == 0 ? 0 : targets[0].length;
        }

        int sampleCount() {
            return features.length;
        }
    }

    private record SplitDataset(double[][] trainingFeatures,
                                double[][] trainingTargets,
                                double[][] validationFeatures,
                                double[][] validationTargets) {
    }

    private record Metrics(double averageMae, double confidencePercent) {
    }

    private record Normalizer(double[] mean, double[] std) {
        static Normalizer fit(double[][] values) {
            int width = values[0].length;
            double[] mean = new double[width];
            double[] std = new double[width];
            for (double[] row : values) {
                for (int column = 0; column < width; column++) {
                    mean[column] += row[column];
                }
            }
            for (int column = 0; column < width; column++) {
                mean[column] /= values.length;
            }
            for (double[] row : values) {
                for (int column = 0; column < width; column++) {
                    double delta = row[column] - mean[column];
                    std[column] += delta * delta;
                }
            }
            for (int column = 0; column < width; column++) {
                std[column] = Math.sqrt(std[column] / values.length);
                if (std[column] < 1.0e-9) {
                    std[column] = 1.0;
                }
            }
            return new Normalizer(mean, std);
        }

        double[][] normalize(double[][] values) {
            double[][] normalized = new double[values.length][];
            for (int row = 0; row < values.length; row++) {
                normalized[row] = normalize(values[row]);
            }
            return normalized;
        }

        double[] normalize(double[] values) {
            double[] normalized = new double[values.length];
            for (int column = 0; column < values.length; column++) {
                normalized[column] = (values[column] - mean[column]) / std[column];
            }
            return normalized;
        }

        double[] denormalize(double[] values) {
            double[] denormalized = new double[values.length];
            for (int column = 0; column < values.length; column++) {
                denormalized[column] = values[column] * std[column] + mean[column];
            }
            return denormalized;
        }
    }
}
