package org.example.parser.wb;

import java.math.BigDecimal;

public record WildberriesParsedProduct(
        String article,
        String name,
        String supplier,
        Long supplierId,
        BigDecimal price,
        BigDecimal feedbackReward,
        Integer stockQuantity,
        BigDecimal rating,
        Integer reviewsCount,
        String cardUrl
) {
    public BigDecimal benefitPercent() {
        if (price == null || feedbackReward == null || price.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return feedbackReward
                .multiply(BigDecimal.valueOf(100))
                .divide(price, 4, java.math.RoundingMode.HALF_UP);
    }
}
