package org.example.forecast;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MultiLayerPerceptronRegressorTest {

    @Test
    void learnsSimpleThreeTargetRegression() {
        Random random = new Random(123L);
        double[][] features = new double[240][2];
        double[][] targets = new double[240][3];

        for (int index = 0; index < features.length; index++) {
            double x1 = random.nextDouble() * 2.0 - 1.0;
            double x2 = random.nextDouble() * 2.0 - 1.0;
            features[index][0] = x1;
            features[index][1] = x2;
            targets[index][0] = 1.8 * x1 - 0.7 * x2 + 0.3;
            targets[index][1] = 0.5 * x1 + 1.2 * x2 + 1.0;
            targets[index][2] = -0.4 * x1 + 0.9 * x2 + 2.5;
        }

        MultiLayerPerceptronRegressor model = new MultiLayerPerceptronRegressor(2, 16, 8, 3, new Random(7L));
        model.fit(features, targets, 500, 24, 0.03, 0.0001, new Random(11L));

        double mae = 0.0;
        int values = 0;
        for (int index = 0; index < features.length; index++) {
            double[] prediction = model.predict(features[index]);
            for (int targetIndex = 0; targetIndex < prediction.length; targetIndex++) {
                mae += Math.abs(prediction[targetIndex] - targets[index][targetIndex]);
                values++;
            }
        }
        mae /= values;

        assertThat(mae).isLessThan(0.08);
    }
}
