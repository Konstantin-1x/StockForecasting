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
import java.math.RoundingMode;

@Entity
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_products_marketplace_article", columnNames = "marketplace_article")
        }
)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;

    @Column(name = "marketplace_article", nullable = false, length = 80)
    private String marketplaceArticle;

    @Column(name = "product_name", nullable = false, length = 500)
    private String name;

    @Column(name = "product_description", length = 2000)
    private String description;

    @Column(name = "base_price", precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "current_stock")
    private Integer currentStock;

    public Long getId() {
        return id;
    }

    public Seller getSeller() {
        return seller;
    }

    public void setSeller(Seller seller) {
        this.seller = seller;
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

    public String getMarketplaceUrl() {
        if (marketplaceArticle == null || marketplaceArticle.isBlank()) {
            return "";
        }
        return "https://www.wildberries.ru/catalog/" + marketplaceArticle.trim() + "/detail.aspx";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public String getBasePriceLabel() {
        return moneyLabel(basePrice);
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(Integer currentStock) {
        this.currentStock = currentStock;
    }

    private static String moneyLabel(BigDecimal value) {
        return value == null ? "" : value.setScale(0, RoundingMode.HALF_UP).toPlainString() + "\u00A0\u20BD";
    }
}
