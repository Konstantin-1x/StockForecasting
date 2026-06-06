package org.example.parser.wb;

public record WildberriesMonitoringRunResult(
        int dueJobs,
        int scheduledJobs,
        int runningJobs
) {
}
