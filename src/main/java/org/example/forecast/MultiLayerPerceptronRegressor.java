package org.example.forecast;

import java.util.Random;

final class MultiLayerPerceptronRegressor {

    private final int inputSize;
    private final int hiddenSize1;
    private final int hiddenSize2;
    private final int outputSize;
    private final double[][] weights1;
    private final double[][] weights2;
    private final double[][] weights3;
    private final double[] bias1;
    private final double[] bias2;
    private final double[] bias3;

    MultiLayerPerceptronRegressor(int inputSize, int hiddenSize1, int hiddenSize2, int outputSize, Random random) {
        this.inputSize = inputSize;
        this.hiddenSize1 = hiddenSize1;
        this.hiddenSize2 = hiddenSize2;
        this.outputSize = outputSize;
        this.weights1 = new double[inputSize][hiddenSize1];
        this.weights2 = new double[hiddenSize1][hiddenSize2];
        this.weights3 = new double[hiddenSize2][outputSize];
        this.bias1 = new double[hiddenSize1];
        this.bias2 = new double[hiddenSize2];
        this.bias3 = new double[outputSize];
        initialize(weights1, inputSize, hiddenSize1, random);
        initialize(weights2, hiddenSize1, hiddenSize2, random);
        initialize(weights3, hiddenSize2, outputSize, random);
    }

    void fit(double[][] features,
             double[][] targets,
             int epochs,
             int batchSize,
             double learningRate,
             double l2Penalty,
             Random random) {
        int samples = features.length;
        int[] order = new int[samples];
        for (int i = 0; i < samples; i++) {
            order[i] = i;
        }

        for (int epoch = 0; epoch < epochs; epoch++) {
            shuffle(order, random);
            for (int batchStart = 0; batchStart < samples; batchStart += batchSize) {
                int batchEnd = Math.min(samples, batchStart + batchSize);
                trainBatch(features, targets, order, batchStart, batchEnd, learningRate, l2Penalty);
            }
        }
    }

    double[] predict(double[] features) {
        ForwardPass pass = forward(features);
        return pass.output;
    }

    private void trainBatch(double[][] features,
                            double[][] targets,
                            int[] order,
                            int batchStart,
                            int batchEnd,
                            double learningRate,
                            double l2Penalty) {
        double[][] gradW1 = new double[inputSize][hiddenSize1];
        double[][] gradW2 = new double[hiddenSize1][hiddenSize2];
        double[][] gradW3 = new double[hiddenSize2][outputSize];
        double[] gradB1 = new double[hiddenSize1];
        double[] gradB2 = new double[hiddenSize2];
        double[] gradB3 = new double[outputSize];

        int batchSize = batchEnd - batchStart;
        for (int index = batchStart; index < batchEnd; index++) {
            int sampleIndex = order[index];
            ForwardPass pass = forward(features[sampleIndex]);
            double[] target = targets[sampleIndex];

            double[] deltaOutput = new double[outputSize];
            for (int outputIndex = 0; outputIndex < outputSize; outputIndex++) {
                deltaOutput[outputIndex] = (pass.output[outputIndex] - target[outputIndex]) * (2.0 / outputSize);
                gradB3[outputIndex] += deltaOutput[outputIndex];
            }
            for (int hiddenIndex = 0; hiddenIndex < hiddenSize2; hiddenIndex++) {
                for (int outputIndex = 0; outputIndex < outputSize; outputIndex++) {
                    gradW3[hiddenIndex][outputIndex] += pass.hidden2[hiddenIndex] * deltaOutput[outputIndex];
                }
            }

            double[] deltaHidden2 = new double[hiddenSize2];
            for (int hiddenIndex = 0; hiddenIndex < hiddenSize2; hiddenIndex++) {
                double weightedGradient = 0.0;
                for (int outputIndex = 0; outputIndex < outputSize; outputIndex++) {
                    weightedGradient += weights3[hiddenIndex][outputIndex] * deltaOutput[outputIndex];
                }
                deltaHidden2[hiddenIndex] = weightedGradient * tanhDerivative(pass.hidden2[hiddenIndex]);
                gradB2[hiddenIndex] += deltaHidden2[hiddenIndex];
            }
            for (int hidden1Index = 0; hidden1Index < hiddenSize1; hidden1Index++) {
                for (int hidden2Index = 0; hidden2Index < hiddenSize2; hidden2Index++) {
                    gradW2[hidden1Index][hidden2Index] += pass.hidden1[hidden1Index] * deltaHidden2[hidden2Index];
                }
            }

            double[] deltaHidden1 = new double[hiddenSize1];
            for (int hiddenIndex = 0; hiddenIndex < hiddenSize1; hiddenIndex++) {
                double weightedGradient = 0.0;
                for (int hidden2Index = 0; hidden2Index < hiddenSize2; hidden2Index++) {
                    weightedGradient += weights2[hiddenIndex][hidden2Index] * deltaHidden2[hidden2Index];
                }
                deltaHidden1[hiddenIndex] = weightedGradient * tanhDerivative(pass.hidden1[hiddenIndex]);
                gradB1[hiddenIndex] += deltaHidden1[hiddenIndex];
            }
            for (int featureIndex = 0; featureIndex < inputSize; featureIndex++) {
                for (int hiddenIndex = 0; hiddenIndex < hiddenSize1; hiddenIndex++) {
                    gradW1[featureIndex][hiddenIndex] += features[sampleIndex][featureIndex] * deltaHidden1[hiddenIndex];
                }
            }
        }

        double inverseBatch = 1.0 / Math.max(1, batchSize);
        for (int featureIndex = 0; featureIndex < inputSize; featureIndex++) {
            for (int hiddenIndex = 0; hiddenIndex < hiddenSize1; hiddenIndex++) {
                weights1[featureIndex][hiddenIndex] -= learningRate * (gradW1[featureIndex][hiddenIndex] * inverseBatch
                        + l2Penalty * weights1[featureIndex][hiddenIndex]);
            }
        }
        for (int hiddenIndex = 0; hiddenIndex < hiddenSize1; hiddenIndex++) {
            bias1[hiddenIndex] -= learningRate * gradB1[hiddenIndex] * inverseBatch;
        }
        for (int hidden1Index = 0; hidden1Index < hiddenSize1; hidden1Index++) {
            for (int hidden2Index = 0; hidden2Index < hiddenSize2; hidden2Index++) {
                weights2[hidden1Index][hidden2Index] -= learningRate * (gradW2[hidden1Index][hidden2Index] * inverseBatch
                        + l2Penalty * weights2[hidden1Index][hidden2Index]);
            }
        }
        for (int hiddenIndex = 0; hiddenIndex < hiddenSize2; hiddenIndex++) {
            bias2[hiddenIndex] -= learningRate * gradB2[hiddenIndex] * inverseBatch;
        }
        for (int hidden2Index = 0; hidden2Index < hiddenSize2; hidden2Index++) {
            for (int outputIndex = 0; outputIndex < outputSize; outputIndex++) {
                weights3[hidden2Index][outputIndex] -= learningRate * (gradW3[hidden2Index][outputIndex] * inverseBatch
                        + l2Penalty * weights3[hidden2Index][outputIndex]);
            }
        }
        for (int outputIndex = 0; outputIndex < outputSize; outputIndex++) {
            bias3[outputIndex] -= learningRate * gradB3[outputIndex] * inverseBatch;
        }
    }

    private ForwardPass forward(double[] features) {
        double[] hidden1 = new double[hiddenSize1];
        for (int hiddenIndex = 0; hiddenIndex < hiddenSize1; hiddenIndex++) {
            double sum = bias1[hiddenIndex];
            for (int featureIndex = 0; featureIndex < inputSize; featureIndex++) {
                sum += features[featureIndex] * weights1[featureIndex][hiddenIndex];
            }
            hidden1[hiddenIndex] = Math.tanh(sum);
        }

        double[] hidden2 = new double[hiddenSize2];
        for (int hiddenIndex = 0; hiddenIndex < hiddenSize2; hiddenIndex++) {
            double sum = bias2[hiddenIndex];
            for (int hidden1Index = 0; hidden1Index < hiddenSize1; hidden1Index++) {
                sum += hidden1[hidden1Index] * weights2[hidden1Index][hiddenIndex];
            }
            hidden2[hiddenIndex] = Math.tanh(sum);
        }

        double[] output = new double[outputSize];
        for (int outputIndex = 0; outputIndex < outputSize; outputIndex++) {
            double sum = bias3[outputIndex];
            for (int hidden2Index = 0; hidden2Index < hiddenSize2; hidden2Index++) {
                sum += hidden2[hidden2Index] * weights3[hidden2Index][outputIndex];
            }
            output[outputIndex] = sum;
        }
        return new ForwardPass(hidden1, hidden2, output);
    }

    private static void initialize(double[][] weights,
                                   int fanIn,
                                   int fanOut,
                                   Random random) {
        double limit = Math.sqrt(6.0 / (fanIn + fanOut));
        for (int left = 0; left < weights.length; left++) {
            for (int right = 0; right < weights[left].length; right++) {
                weights[left][right] = (random.nextDouble() * 2.0 - 1.0) * limit;
            }
        }
    }

    private static double tanhDerivative(double activation) {
        return 1.0 - activation * activation;
    }

    private static void shuffle(int[] values, Random random) {
        for (int index = values.length - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            int temp = values[index];
            values[index] = values[other];
            values[other] = temp;
        }
    }

    private record ForwardPass(double[] hidden1, double[] hidden2, double[] output) {
    }
}
