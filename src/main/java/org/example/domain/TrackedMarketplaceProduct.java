package org.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Entity
@Table(
        name = "tracked_marketplace_products",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tracked_marketplace_products_article", columnNames = "marketplace_article")
        },
        indexes = {
                @Index(name = "idx_tracked_marketplace_products_discovered_at", columnList = "discovered_at"),
                @Index(name = "idx_tracked_marketplace_products_active", columnList = "active")
        }
)
public class TrackedMarketplaceProduct {

    private static final DateTimeFormatter VIEW_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tracked_product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;

    @Column(name = "marketplace_article", nullable = false, length = 80)
    private String marketplaceArticle;

    @Column(name = "product_name", nullable = false, length = 500)
    private String productName;

    @Column(name = "seller_name", length = 250)
    private String sellerName;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "discovered_price", precision = 12, scale = 2)
    private BigDecimal discoveredPrice;

    @Column(name = "feedback_reward", precision = 12, scale = 2)
    private BigDecimal feedbackReward;

    @Column(name = "benefit_percent", precision = 10, scale = 4)
    private BigDecimal benefitPercent;

    @Column(name = "discovered_stock")
    private Integer discoveredStock;

    @Column(name = "card_url", nullable = false, length = 600)
    private String cardUrl;

    @Column(name = "detail_url", nullable = false, length = 600)
    private String detailUrl;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "completed_at")
    private Instant completedAt;

    public Long getId() {
        return id;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public String getMarketplaceArticle() {
        return marketplaceArticle;
    }

    public void setMarketplaceArticle(String marketplaceArticle) {
        this.marketplaceArticle = marketplaceArticle;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public BigDecimal getDiscoveredPrice() {
        return discoveredPrice;
    }

    public void setDiscoveredPrice(BigDecimal discoveredPrice) {
        this.discoveredPrice = discoveredPrice;
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

    public Integer getDiscoveredStock() {
        return discoveredStock;
    }

    public void setDiscoveredStock(Integer discoveredStock) {
        this.discoveredStock = discoveredStock;
    }

    public String getCardUrl() {
        return cardUrl;
    }

    public void setCardUrl(String cardUrl) {
        this.cardUrl = cardUrl;
    }

    public String getDetailUrl() {
        return detailUrl;
    }

    public void setDetailUrl(String detailUrl) {
        this.detailUrl = detailUrl;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }

    public void setDiscoveredAt(Instant discoveredAt) {
        this.discoveredAt = discoveredAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getDiscoveredAtLabel() {
        return discoveredAt == null ? "" : VIEW_DATE_FORMATTER.format(discoveredAt);
    }
}
