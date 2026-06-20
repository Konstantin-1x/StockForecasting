package org.example.parser.wb;

import java.math.BigDecimal;
import java.time.Instant;

public record WildberriesProductDetailTestResult(
        boolean success,
        Integer httpStatus,
        String errorMessage,
        String article,
        String name,
        String supplier,
        BigDecimal price,
        Integer stockQuantity,
        BigDecimal feedbackReward,
        BigDecimal benefitPercent,
        BigDecimal rating,
        Integer reviewsCount,
        int responseLength,
        String responsePreview,
        Instant checkedAt
) {
}
