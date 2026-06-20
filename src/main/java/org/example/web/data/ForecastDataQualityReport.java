package org.example.web.data;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record ForecastDataQualityReport(
        Instant checkedAt,
        List<ForecastDataQualityCheck> checks
) {
    private static final DateTimeFormatter VIEW_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    public boolean hasCriticalIssues() {
        return checks.stream().anyMatch(check -> "BAD".equals(check.status()));
    }

    public List<ForecastDataQualityCheck> visibleChecks() {
        return checks.stream()
                .filter(ForecastDataQualityCheck::visible)
                .filter(check -> !"OK".equals(check.status()))
                .toList();
    }

    public String checkedAtLabel() {
        return checkedAt == null ? "" : VIEW_DATE_FORMATTER.format(checkedAt);
    }
}
