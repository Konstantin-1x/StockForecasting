package org.example.forecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Service
public class NeuralForecastJobService {

    private static final Logger log = LoggerFactory.getLogger(NeuralForecastJobService.class);

    private final NeuralPromotionForecastService forecastService;
    private final ExecutorService neuralForecastExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<NeuralForecastJobStatus> status =
            new AtomicReference<>(NeuralForecastJobStatus.idle());

    public NeuralForecastJobService(NeuralPromotionForecastService forecastService,
                                    @Qualifier("neuralForecastExecutor") ExecutorService neuralForecastExecutor) {
        this.forecastService = forecastService;
        this.neuralForecastExecutor = neuralForecastExecutor;
    }

    public boolean startRecalculation() {
        return startJob(forecastService::recalculateForecasts, "Neural forecast background job");
    }

    public boolean startRecalculationForMarketplaceArticles(Set<String> marketplaceArticles) {
        return startJob(() -> forecastService.recalculateForecastsForMarketplaceArticles(marketplaceArticles),
                "Seller neural forecast background job");
    }

    private boolean startJob(Supplier<NeuralForecastRunResult> job, String logLabel) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }

        Instant startedAt = Instant.now();
        status.set(NeuralForecastJobStatus.running(startedAt));
        log.info("{} started at {}", logLabel, startedAt);

        CompletableFuture
                .supplyAsync(job, neuralForecastExecutor)
                .whenComplete((result, error) -> {
                    Instant finishedAt = Instant.now();
                    running.set(false);
                    if (error != null) {
                        String message = "Нейросетевой пересчет завершился ошибкой: " + error.getMessage();
                        status.set(NeuralForecastJobStatus.finished(startedAt, finishedAt, message));
                        log.warn("Neural forecast background job failed", error);
                        return;
                    }

                    status.set(NeuralForecastJobStatus.finished(startedAt, finishedAt, result.statusMessage()));
                    log.info("{} finished at {}: {}", logLabel, finishedAt, result.statusMessage());
                });
        return true;
    }

    public NeuralForecastJobStatus currentStatus() {
        return status.get();
    }
}
