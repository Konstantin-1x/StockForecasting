package org.example.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Forecast layer tests")
class DiplomaForecastLayerTest {

    @Test
    @DisplayName("MLP model learns promo dataset and predicts campaign parameters")
    void mlpModelLearnsPromoDatasetAndPredictsCampaignParameters() {
        Random dataRandom = new Random(2026L);
        int samples = 320;
        double[][] features = new double[samples][4];
        double[][] targets = new double[samples][3];

        for (int index = 0; index < samples; index++) {
            double normalizedPrice = 0.2 + dataRandom.nextDouble() * 0.8;
            double normalizedStock = 0.1 + dataRandom.nextDouble() * 0.9;
            double normalizedRating = 0.6 + dataRandom.nextDouble() * 0.4;
            double normalizedReviews = dataRandom.nextDouble();

            features[index][0] = normalizedPrice;
            features[index][1] = normalizedStock;
            features[index][2] = normalizedRating;
            features[index][3] = normalizedReviews;

            targets[index][0] = 0.10 + normalizedPrice * 0.55 + normalizedRating * 0.10;
            targets[index][1] = 0.12 + normalizedStock * 0.45 - normalizedReviews * 0.08;
            targets[index][2] = 0.08 + normalizedStock * 0.60 - normalizedRating * 0.08;
        }

        MultiLayerPerceptronRegressor model = new MultiLayerPerceptronRegressor(4, 40, 20, 3, new Random(42L));
        model.fit(features, targets, 650, 24, 0.015, 0.0005, new Random(43L));

        double mae = averageAbsoluteError(model, features, targets);
        double[] sellerProductFeatures = {0.55, 0.70, 0.88, 0.35};
        double[] prediction = model.predict(sellerProductFeatures);

        System.out.println("[REPORT] Forecast model: MLP input=4 hidden1=40 hidden2=20 output=3");
        System.out.println("[REPORT] Training dataset: samples=" + samples
                + ", targets=reward_rate/campaign_days/stock_during_campaign");
        double rewardRate = 30.0 + prediction[0] * 300.0;
        double campaignDays = 1.0 + prediction[1] * 30.0;
        double stockDuringCampaign = prediction[2] * 80.0;

        System.out.println("[REPORT] Validation metric: normalized_MAE=" + round(mae));
        System.out.println("[REPORT] Forecast for seller product:");
        System.out.println("[REPORT]   reward_rate=" + round(rewardRate)
                + ", campaign_days=" + round(campaignDays)
                + ", stock_during_campaign=" + round(stockDuringCampaign));

        assertThat(mae).isLessThan(0.03);
        assertThat(rewardRate).isBetween(170.0, 185.0);
        assertThat(campaignDays).isBetween(13.0, 16.0);
        assertThat(stockDuringCampaign).isBetween(33.0, 37.0);
    }

    private static double averageAbsoluteError(MultiLayerPerceptronRegressor model,
                                               double[][] features,
                                               double[][] targets) {
        double error = 0.0;
        int values = 0;
        for (int index = 0; index < features.length; index++) {
            double[] prediction = model.predict(features[index]);
            for (int targetIndex = 0; targetIndex < prediction.length; targetIndex++) {
                error += Math.abs(prediction[targetIndex] - targets[index][targetIndex]);
                values++;
            }
        }
        return error / Math.max(1, values);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
