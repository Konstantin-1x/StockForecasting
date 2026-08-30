package org.example.forecast;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forecast.neural")
public class NeuralForecastProperties {

    private int trainingEpochs = 240;
    private int batchSize = 16;
    private int maxTrainingSamples = 1800;
    private double learningRate = 0.008;
    private double l2Penalty = 0.0007;
    private int sellerForecastCategoryCandidateLimit = 140;
    private int sellerForecastParentCandidateLimit = 220;
    private int sellerForecastPriceCandidateLimit = 260;
    private int sellerForecastGlobalCandidateLimit = 220;

    public int getTrainingEpochs() {
        return trainingEpochs;
    }

    public void setTrainingEpochs(int trainingEpochs) {
        this.trainingEpochs = Math.max(1, trainingEpochs);
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = Math.max(1, batchSize);
    }

    public int getMaxTrainingSamples() {
        return maxTrainingSamples;
    }

    public void setMaxTrainingSamples(int maxTrainingSamples) {
        this.maxTrainingSamples = Math.max(32, maxTrainingSamples);
    }

    public double getLearningRate() {
        return learningRate;
    }

    public void setLearningRate(double learningRate) {
        this.learningRate = learningRate > 0.0 ? learningRate : this.learningRate;
    }

    public double getL2Penalty() {
        return l2Penalty;
    }

    public void setL2Penalty(double l2Penalty) {
        this.l2Penalty = Math.max(0.0, l2Penalty);
    }

    public int getSellerForecastCategoryCandidateLimit() {
        return sellerForecastCategoryCandidateLimit;
    }

    public void setSellerForecastCategoryCandidateLimit(int sellerForecastCategoryCandidateLimit) {
        this.sellerForecastCategoryCandidateLimit = Math.max(1, sellerForecastCategoryCandidateLimit);
    }

    public int getSellerForecastParentCandidateLimit() {
        return sellerForecastParentCandidateLimit;
    }

    public void setSellerForecastParentCandidateLimit(int sellerForecastParentCandidateLimit) {
        this.sellerForecastParentCandidateLimit = Math.max(1, sellerForecastParentCandidateLimit);
    }

    public int getSellerForecastPriceCandidateLimit() {
        return sellerForecastPriceCandidateLimit;
    }

    public void setSellerForecastPriceCandidateLimit(int sellerForecastPriceCandidateLimit) {
        this.sellerForecastPriceCandidateLimit = Math.max(1, sellerForecastPriceCandidateLimit);
    }

    public int getSellerForecastGlobalCandidateLimit() {
        return sellerForecastGlobalCandidateLimit;
    }

    public void setSellerForecastGlobalCandidateLimit(int sellerForecastGlobalCandidateLimit) {
        this.sellerForecastGlobalCandidateLimit = Math.max(1, sellerForecastGlobalCandidateLimit);
    }
}
