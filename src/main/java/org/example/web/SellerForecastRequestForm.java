package org.example.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class SellerForecastRequestForm {

    @NotNull(message = "Выберите товар для прогнозирования.")
    private Long productId;

    @NotNull(message = "Укажите бюджет промо акции.")
    @DecimalMin(value = "1.0", message = "Бюджет должен быть не меньше 1 рубля.")
    private BigDecimal promotionBudget;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getPromotionBudget() {
        return promotionBudget;
    }

    public void setPromotionBudget(BigDecimal promotionBudget) {
        this.promotionBudget = promotionBudget;
    }
}
