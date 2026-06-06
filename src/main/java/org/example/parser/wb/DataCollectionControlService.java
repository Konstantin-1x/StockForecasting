package org.example.parser.wb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DataCollectionControlService {

    private static final Logger log = LoggerFactory.getLogger(DataCollectionControlService.class);

    private final WildberriesParserProperties properties;
    private final WildberriesProductDiscoveryService discoveryService;
    private final WildberriesProductMonitoringService monitoringService;
    private final AtomicBoolean backgroundRun = new AtomicBoolean(false);

    public DataCollectionControlService(WildberriesParserProperties properties,
                                        WildberriesProductDiscoveryService discoveryService,
                                        WildberriesProductMonitoringService monitoringService) {
        this.properties = properties;
        this.discoveryService = discoveryService;
        this.monitoringService = monitoringService;
    }

    public DataCollectionStatus startCollection() {
        properties.setContinuousScanEnabled(true);
        properties.setMonitoringEnabled(true);

        boolean startedNow = backgroundRun.compareAndSet(false, true);
        if (startedNow) {
            CompletableFuture.runAsync(() -> {
                try {
                    discoveryService.runDiscoveryScan();
                    monitoringService.runDueJobs();
                } catch (RuntimeException e) {
                    log.warn("Manual Wildberries data collection failed: {}", e.getMessage());
                } finally {
                    backgroundRun.set(false);
                }
            });
        }

        String message = startedNow
                ? "Сбор данных включен. Первый проход запущен в фоне."
                : "Сбор данных включен. Первый проход уже выполняется.";
        return currentStatus(message, startedNow);
    }

    public DataCollectionStatus stopCollection() {
        properties.setContinuousScanEnabled(false);
        properties.setMonitoringEnabled(false);

        String message = discoveryService.isScanRunning() || backgroundRun.get() || monitoringService.runningJobsCount() > 0
                ? "Новые запуски сбора остановлены. Текущие задачи завершатся самостоятельно."
                : "Сбор данных остановлен.";
        return currentStatus(message, false);
    }

    public DataCollectionStatus currentStatus() {
        return currentStatus(defaultMessage(), backgroundRun.get());
    }

    private DataCollectionStatus currentStatus(String message, boolean backgroundRunStarted) {
        return new DataCollectionStatus(
                properties.isContinuousScanEnabled(),
                properties.isMonitoringEnabled(),
                backgroundRunStarted,
                discoveryService.isScanRunning(),
                monitoringService.runningJobsCount(),
                message
        );
    }

    private String defaultMessage() {
        if (properties.isContinuousScanEnabled() || properties.isMonitoringEnabled()) {
            return "Плановый сбор данных включен.";
        }
        return "Плановый сбор данных остановлен.";
    }
}
