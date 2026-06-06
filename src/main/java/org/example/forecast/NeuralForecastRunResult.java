package org.example.forecast;

public record NeuralForecastRunResult(
        int trackedProductsConsidered,
        int trainingSamples,
        int forecastsSaved,
        int skippedProducts,
        String statusMessage
) {
}
