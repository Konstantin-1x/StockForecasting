package org.example.forecast;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record NeuralForecastRunResult(
        int trackedProductsConsidered,
        int trainingSamples,
        int forecastsSaved,
        int skippedProducts,
        String statusMessage,
        ForecastScenarioSummary scenario
) {
    public NeuralForecastRunResult(int trackedProductsConsidered,
                                   int trainingSamples,
                                   int forecastsSaved,
                                   int skippedProducts,
                                   String statusMessage) {
        this(trackedProductsConsidered, trainingSamples, forecastsSaved, skippedProducts, statusMessage, null);
    }

    public record ForecastScenarioSummary(
            String productName,
            BigDecimal budget,
            BigDecimal reviewReward,
            int plannedReviews,
            int collectionDays,
            int platformCampaignDays,
            int stockAtStart,
            BigDecimal confidencePercent,
            int trainingSamples,
            String modelLabel
    ) {
        public String budgetLabel() {
            return moneyLabel(budget);
        }

        public String reviewRewardLabel() {
            return moneyLabel(reviewReward);
        }

        private static String moneyLabel(BigDecimal value) {
            return value == null ? "" : value.setScale(0, RoundingMode.HALF_UP).toPlainString() + "\u00A0\u20BD";
        }
    }
}
