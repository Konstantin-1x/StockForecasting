package org.example.parser.wb;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record WildberriesProductDetails(
        String article,
        String name,
        String supplier,
        Long supplierId,
        BigDecimal price,
        BigDecimal feedbackReward,
        Integer stockQuantity,
        BigDecimal rating,
        Integer reviewsCount,
        String cardUrl,
        String detailUrl
) {
    public BigDecimal benefitPercent() {
        if (price == null || feedbackReward == null || price.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return feedbackReward
                .multiply(BigDecimal.valueOf(100))
                .divide(price, 4, RoundingMode.HALF_UP);
    }
}
