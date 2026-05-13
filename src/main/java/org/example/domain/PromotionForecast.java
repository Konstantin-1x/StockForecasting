package org.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "promotion_forecasts")
public class PromotionForecast {

    private static final DateTimeFormatter VIEW_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "forecast_id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tracked_product_id")
    private TrackedMarketplaceProduct trackedProduct;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt = Instant.now();

    @Column(name = "promotion_cost_forecast", precision = 14, scale = 2)
    private BigDecimal promotionCostForecast;

    @Column(name = "promotion_purchase_forecast")
    private Integer promotionPurchaseForecast;

    @Column(name = "sellout_days_forecast")
    private Integer selloutDaysForecast;

    @Column(name = "promotion_start_hours_forecast")
    private Integer promotionStartHoursForecast;

    @Column(name = "promotion_stock_forecast")
    private Integer promotionStockForecast;

    @Column(name = "confidence_interval", precision = 6, scale = 2)
    private BigDecimal confidenceInterval;

    @Column(name = "forecast_model", length = 80)
    private String forecastModel;

    @Column(name = "training_sample_count")
    private Integer trainingSampleCount;

    @Column(name = "validation_mae", precision = 10, scale = 4)
    private BigDecimal validationMae;

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public TrackedMarketplaceProduct getTrackedProduct() {
        return trackedProduct;
    }

    public void setTrackedProduct(TrackedMarketplaceProduct trackedProduct) {
        this.trackedProduct = trackedProduct;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public BigDecimal getPromotionCostForecast() {
        return promotionCostForecast;
    }

    public void setPromotionCostForecast(BigDecimal promotionCostForecast) {
        this.promotionCostForecast = promotionCostForecast;
    }

    public Integer getPromotionPurchaseForecast() {
        return promotionPurchaseForecast;
    }

    public void setPromotionPurchaseForecast(Integer promotionPurchaseForecast) {
        this.promotionPurchaseForecast = promotionPurchaseForecast;
    }

    public Integer getSelloutDaysForecast() {
        return selloutDaysForecast;
    }

    public void setSelloutDaysForecast(Integer selloutDaysForecast) {
        this.selloutDaysForecast = selloutDaysForecast;
    }

    public Integer getPromotionStartHoursForecast() {
        return promotionStartHoursForecast;
    }

    public void setPromotionStartHoursForecast(Integer promotionStartHoursForecast) {
        this.promotionStartHoursForecast = promotionStartHoursForecast;
    }

    public Integer getPromotionStockForecast() {
        return promotionStockForecast;
    }

    public void setPromotionStockForecast(Integer promotionStockForecast) {
        this.promotionStockForecast = promotionStockForecast;
    }

    public BigDecimal getConfidenceInterval() {
        return confidenceInterval;
    }

    public void setConfidenceInterval(BigDecimal confidenceInterval) {
        this.confidenceInterval = confidenceInterval;
    }

    public String getForecastModel() {
        return forecastModel;
    }

    public void setForecastModel(String forecastModel) {
        this.forecastModel = forecastModel;
    }

    public Integer getTrainingSampleCount() {
        return trainingSampleCount;
    }

    public void setTrainingSampleCount(Integer trainingSampleCount) {
        this.trainingSampleCount = trainingSampleCount;
    }

    public BigDecimal getValidationMae() {
        return validationMae;
    }

    public void setValidationMae(BigDecimal validationMae) {
        this.validationMae = validationMae;
    }

    public String getCalculatedAtLabel() {
        return calculatedAt == null ? "" : VIEW_DATE_FORMATTER.format(calculatedAt);
    }

    public String getDisplayProductName() {
        if (trackedProduct != null && trackedProduct.getProductName() != null && !trackedProduct.getProductName().isBlank()) {
            return trackedProduct.getProductName();
        }
        return product == null ? "" : product.getName();
    }

    public String getDisplayMarketplaceArticle() {
        if (trackedProduct != null && trackedProduct.getMarketplaceArticle() != null && !trackedProduct.getMarketplaceArticle().isBlank()) {
            return trackedProduct.getMarketplaceArticle();
        }
        return product == null ? "" : product.getMarketplaceArticle();
    }

    public String getPromotionStartLabel() {
        if (promotionStartHoursForecast == null) {
            return "";
        }
        int hours = Math.max(0, promotionStartHoursForecast);
        int days = hours / 24;
        int remainingHours = hours % 24;
        if (days == 0) {
            return hours + " ч.";
        }
        if (remainingHours == 0) {
            return days + " д.";
        }
        return days + " д. " + remainingHours + " ч.";
    }

    public String getPromotionStockLabel() {
        return promotionStockForecast == null ? "" : String.valueOf(promotionStockForecast);
    }

    public String getConfidenceLabel() {
        return confidenceInterval == null ? "" : confidenceInterval.stripTrailingZeros().toPlainString() + "%";
    }

    public String getValidationMaeLabel() {
        return validationMae == null ? "" : validationMae.stripTrailingZeros().toPlainString();
    }
}
