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

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt = Instant.now();

    @Column(name = "promotion_cost_forecast", precision = 14, scale = 2)
    private BigDecimal promotionCostForecast;

    @Column(name = "promotion_purchase_forecast")
    private Integer promotionPurchaseForecast;

    @Column(name = "sellout_days_forecast")
    private Integer selloutDaysForecast;

    @Column(name = "confidence_interval", precision = 6, scale = 2)
    private BigDecimal confidenceInterval;

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
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

    public BigDecimal getConfidenceInterval() {
        return confidenceInterval;
    }

    public void setConfidenceInterval(BigDecimal confidenceInterval) {
        this.confidenceInterval = confidenceInterval;
    }

    public String getCalculatedAtLabel() {
        return calculatedAt == null ? "" : VIEW_DATE_FORMATTER.format(calculatedAt);
    }
}
