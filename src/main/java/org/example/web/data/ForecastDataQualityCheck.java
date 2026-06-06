package org.example.web.data;

public record ForecastDataQualityCheck(
        String title,
        String value,
        String status,
        String details
) {
}
