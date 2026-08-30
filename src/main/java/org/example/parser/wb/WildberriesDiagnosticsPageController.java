package org.example.parser.wb;

import org.example.repository.MarketplaceMonitoringJobRepository;
import org.example.repository.MarketplaceProductSnapshotRepository;
import org.example.repository.TrackedMarketplaceProductRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class WildberriesDiagnosticsPageController {

    private final WildberriesHttpClient httpClient;
    private final WildberriesProductDiscoveryService discoveryService;
    private final WildberriesProductMonitoringService monitoringService;
    private final TrackedMarketplaceProductRepository trackedProductRepository;
    private final MarketplaceMonitoringJobRepository monitoringJobRepository;
    private final MarketplaceProductSnapshotRepository snapshotRepository;
    private final DataCollectionControlService dataCollectionControlService;

    public WildberriesDiagnosticsPageController(WildberriesHttpClient httpClient,
                                                WildberriesProductDiscoveryService discoveryService,
                                                WildberriesProductMonitoringService monitoringService,
                                                TrackedMarketplaceProductRepository trackedProductRepository,
                                                MarketplaceMonitoringJobRepository monitoringJobRepository,
                                                MarketplaceProductSnapshotRepository snapshotRepository,
                                                DataCollectionControlService dataCollectionControlService) {
        this.httpClient = httpClient;
        this.discoveryService = discoveryService;
        this.monitoringService = monitoringService;
        this.trackedProductRepository = trackedProductRepository;
        this.monitoringJobRepository = monitoringJobRepository;
        this.snapshotRepository = snapshotRepository;
        this.dataCollectionControlService = dataCollectionControlService;
    }

    @GetMapping("/admin/diagnostics")
    public String diagnostics(Model model) {
        Map<String, Object> diagnostics = new LinkedHashMap<>(httpClient.diagnostics());
        diagnostics.put("baselineReady", discoveryService.isBaselineReady());
        diagnostics.put("scanRunning", discoveryService.isScanRunning());
        diagnostics.put("seenArticlesInMemory", discoveryService.seenArticlesCount());
        diagnostics.put("trackedProducts", trackedProductRepository.count());
        diagnostics.put("activeTrackedProducts", trackedProductRepository.countByActiveTrue());
        diagnostics.put("activeMonitoringJobs", monitoringJobRepository.countByFinishedFalse());
        diagnostics.put("runningMonitoringJobs", monitoringService.runningJobsCount());
        diagnostics.put("productSnapshots", snapshotRepository.count());

        model.addAttribute("diagnostics", diagnostics);
        model.addAttribute("diagnosticAlerts", notableDiagnostics(diagnostics));
        model.addAttribute("dataCollectionStatus", dataCollectionControlService.currentStatus());
        return "diagnostics";
    }

    private static Map<String, Object> notableDiagnostics(Map<String, Object> diagnostics) {
        Map<String, Object> alerts = new LinkedHashMap<>();
        putIfNonPositive(alerts, diagnostics, "cookieSets");
        putIfPositive(alerts, diagnostics, "cookieHttp498WindowHits");
        putIfPositive(alerts, diagnostics, "proxiesCoolingDown");
        if (Boolean.TRUE.equals(diagnostics.get("proxyEnabled"))) {
            putIfNonPositive(alerts, diagnostics, "proxiesAvailable");
        }
        putIfPositive(alerts, diagnostics, "runningMonitoringJobs");
        putIfTrue(alerts, diagnostics, "scanRunning");
        putIfNonPositive(alerts, diagnostics, "activeTrackedProducts");
        putIfNonPositive(alerts, diagnostics, "productSnapshots");
        return alerts;
    }

    private static void putIfPositive(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Number number && number.longValue() > 0) {
            target.put(key, value);
        }
    }

    private static void putIfNonPositive(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Number number && number.longValue() <= 0) {
            target.put(key, value);
        }
    }

    private static void putIfTrue(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (Boolean.TRUE.equals(value)) {
            target.put(key, value);
        }
    }
}
