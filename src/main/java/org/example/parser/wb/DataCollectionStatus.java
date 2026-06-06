package org.example.parser.wb;

public record DataCollectionStatus(
        boolean discoveryEnabled,
        boolean monitoringEnabled,
        boolean backgroundRunStarted,
        boolean discoveryRunning,
        int runningMonitoringJobs,
        String message
) {
    public boolean enabled() {
        return discoveryEnabled || monitoringEnabled;
    }
}
