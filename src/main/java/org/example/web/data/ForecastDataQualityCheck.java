package org.example.web.data;

public record ForecastDataQualityCheck(
        String title,
        String value,
        String status,
        String details,
        boolean visible
) {
    public ForecastDataQualityCheck(String title, String value, String status, String details) {
        this(title, value, status, details, true);
    }
}
