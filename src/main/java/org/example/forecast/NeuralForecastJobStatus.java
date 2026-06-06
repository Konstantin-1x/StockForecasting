package org.example.forecast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record NeuralForecastJobStatus(
        boolean running,
        Instant startedAt,
        Instant finishedAt,
        String message
) {
    private static final DateTimeFormatter VIEW_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    static NeuralForecastJobStatus idle() {
        return new NeuralForecastJobStatus(false, null, null, "Нейросетевой пересчет еще не запускался.");
    }

    static NeuralForecastJobStatus running(Instant startedAt) {
        return new NeuralForecastJobStatus(true, startedAt, null, "Нейросетевой пересчет выполняется в фоне.");
    }

    static NeuralForecastJobStatus finished(Instant startedAt, Instant finishedAt, String message) {
        return new NeuralForecastJobStatus(false, startedAt, finishedAt, message);
    }

    public String getStartedAtLabel() {
        return startedAt == null ? "" : VIEW_DATE_FORMATTER.format(startedAt);
    }

    public String getFinishedAtLabel() {
        return finishedAt == null ? "" : VIEW_DATE_FORMATTER.format(finishedAt);
    }
}
