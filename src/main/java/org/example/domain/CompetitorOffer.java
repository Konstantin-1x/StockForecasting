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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Entity
@Table(
        name = "competitor_offers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_competitor_offers_marketplace_article", columnNames = "marketplace_article")
        }
)
public class CompetitorOffer {

    private static final DateTimeFormatter VIEW_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "competitor_offer_id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;

    @Column(name = "competitor_name", length = 250)
    private String competitorName;

    @Column(name = "product_name", nullable = false, length = 500)
    private String productName;

    @Column(name = "competitor_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal competitorPrice;

    @Column(name = "competitor_discount", precision = 12, scale = 2)
    private BigDecimal competitorDiscount;

    @Column(name = "feedback_reward", precision = 12, scale = 2)
    private BigDecimal feedbackReward;

    @Column(name = "benefit_percent", precision = 10, scale = 4)
    private BigDecimal benefitPercent;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(name = "marketplace_article", nullable = false, length = 80)
    private String marketplaceArticle;

    @Column(name = "card_url", nullable = false, length = 600)
    private String cardUrl;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    public Long getId() {
        return id;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public String getCompetitorName() {
        return competitorName;
    }

    public void setCompetitorName(String competitorName) {
        this.competitorName = competitorName;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getCompetitorPrice() {
        return competitorPrice;
    }

    public void setCompetitorPrice(BigDecimal competitorPrice) {
        this.competitorPrice = competitorPrice;
    }

    public BigDecimal getCompetitorDiscount() {
        return competitorDiscount;
    }

    public void setCompetitorDiscount(BigDecimal competitorDiscount) {
        this.competitorDiscount = competitorDiscount;
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

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public String getMarketplaceArticle() {
        return marketplaceArticle;
    }

    public void setMarketplaceArticle(String marketplaceArticle) {
        this.marketplaceArticle = marketplaceArticle;
    }

    public String getCardUrl() {
        return cardUrl;
    }

    public void setCardUrl(String cardUrl) {
        this.cardUrl = cardUrl;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(Instant collectedAt) {
        this.collectedAt = collectedAt;
    }

    public String getCollectedAtLabel() {
        return collectedAt == null ? "" : VIEW_DATE_FORMATTER.format(collectedAt);
    }
}
