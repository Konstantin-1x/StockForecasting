package org.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "marketplace_product_snapshots",
        indexes = {
                @Index(name = "idx_marketplace_product_snapshots_product_time", columnList = "tracked_product_id,collected_at")
        }
)
public class MarketplaceProductSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tracked_product_id", nullable = false)
    private TrackedMarketplaceProduct product;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(name = "feedback_reward", precision = 12, scale = 2)
    private BigDecimal feedbackReward;

    @Column(name = "benefit_percent", precision = 10, scale = 4)
    private BigDecimal benefitPercent;

    @Column(name = "rating", precision = 4, scale = 2)
    private BigDecimal rating;

    @Column(name = "reviews_count")
    private Integer reviewsCount;

    @Lob
    @Column(name = "raw_json", columnDefinition = "text")
    private String rawJson;

    public Long getId() {
        return id;
    }

    public TrackedMarketplaceProduct getProduct() {
        return product;
    }

    public void setProduct(TrackedMarketplaceProduct product) {
        this.product = product;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(Instant collectedAt) {
        this.collectedAt = collectedAt;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public BigDecimal getFeedbackReward() {
        return feedbackReward;
    }

    public void setFeedbackReward(BigDecimal feedbackReward) {
        this.feedbackReward = feedbackReward;
    }

    public BigDecimal getBenefitPercent() {
        return benefitPercent;
    }

    public void setBenefitPercent(BigDecimal benefitPercent) {
        this.benefitPercent = benefitPercent;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public Integer getReviewsCount() {
        return reviewsCount;
    }

    public void setReviewsCount(Integer reviewsCount) {
        this.reviewsCount = reviewsCount;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }
}
