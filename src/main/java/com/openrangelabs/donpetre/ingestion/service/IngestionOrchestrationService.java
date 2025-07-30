package com.openrangelabs.donpetre.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.openrangelabs.donpetre.ingestion.connector.DataConnector;
import com.openrangelabs.donpetre.ingestion.entity.ConnectorConfig;
import com.openrangelabs.donpetre.ingestion.model.SyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for orchestrating ingestion operations across multiple connectors
 */
@Service
public class IngestionOrchestrationService {

    private static final Logger logger = LoggerFactory.getLogger(IngestionOrchestrationService.class);

    private final Map<String, DataConnector> connectors;
    private final IngestionJobService jobService;
    private final ConnectorConfigService configService;
    private final Map<String, LocalDateTime> lastSyncTimes = new ConcurrentHashMap<>();

    @Value("${ingestion.jobs.max-concurrent-jobs:5}")
    private int maxConcurrentJobs;

    @Autowired
    public IngestionOrchestrationService(
            List<DataConnector> connectorList,
            IngestionJobService jobService,
            ConnectorConfigService configService) {

        this.connectors = connectorList.stream()
                .collect(Collectors.toMap(DataConnector::getConnectorType, Function.identity()));
        this.jobService = jobService;
        this.configService = configService;

        logger.info("Initialized orchestration service with connectors: {}",
                connectors.keySet());
    }

    /**
     * Trigger full synchronization for a specific connector configuration
     */
    public Mono<SyncResult> triggerFullSync(String connectorType, String configName) {
        return configService.getConfiguration(connectorType, configName)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Configuration not found: " + connectorType + "/" + configName)))
                .flatMap(config -> {
                    if (!config.getEnabled()) {
                        return Mono.error(new IllegalStateException(
                                "Connector is disabled: " + connectorType + "/" + configName));
                    }

                    DataConnector connector = connectors.get(connectorType);
                    if (connector == null) {
                        return Mono.error(new IllegalArgumentException(
                                "No connector found for type: " + connectorType));
                    }

                    return checkConcurrentJobsLimit()
                            .then(connector.performSync(config))
                            .doOnSuccess(result ->
                                    lastSyncTimes.put(getConfigKey(config), LocalDateTime.now()))
                            .doOnSuccess(result ->
                                    logger.info("Full sync completed for {}/{}: processed={}, failed={}",
                                            connectorType, configName, result.getProcessedCount(), result.getFailedCount()));
                });
    }

    /**
     * Trigger incremental synchronization for a specific connector configuration
     */
    public Mono<SyncResult> triggerIncrementalSync(String connectorType, String configName) {
        return configService.getConfiguration(connectorType, configName)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Configuration not found: " + connectorType + "/" + configName)))
                .flatMap(config -> {
                    if (!config.getEnabled()) {
                        return Mono.error(new IllegalStateException(
                                "Connector is disabled: " + connectorType + "/" + configName));
                    }

                    DataConnector connector = connectors.get(connectorType);
                    if (connector == null) {
                        return Mono.error(new IllegalArgumentException(
                                "No connector found for type: " + connectorType));
                    }

                    return checkConcurrentJobsLimit()
                            .then(jobService.getLatestSuccessfulJob(config.getId()))
                            .map(job -> job.getLastSyncCursor())
                            .defaultIfEmpty(null)
                            .flatMap(lastCursor -> connector.performIncrementalSync(config, lastCursor))
                            .doOnSuccess(result ->
                                    lastSyncTimes.put(getConfigKey(config), LocalDateTime.now()))
                            .doOnSuccess(result ->
                                    logger.info("Incremental sync completed for {}/{}: processed={}, failed={}",
                                            connectorType, configName, result.getProcessedCount(), result.getFailedCount()));
                });
    }

    /**
     * Schedule incremental sync (used by scheduler)
     */
    public Mono<SyncResult> scheduleIncrementalSync(ConnectorConfig config) {
        return triggerIncrementalSync(config.getConnectorType(), config.getName());
    }

    /**
     * Test connection for a connector configuration
     */
    public Mono<Boolean> testConnection(String connectorType, String configName) {
        return configService.getConfiguration(connectorType, configName)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Configuration not found: " + connectorType + "/" + configName)))
                .flatMap(config -> {
                    DataConnector connector = connectors.get(connectorType);
                    if (connector == null) {
                        return Mono.error(new IllegalArgumentException(
                                "No connector found for type: " + connectorType));
                    }

                    return connector.testConnection(config)
                            .doOnNext(result ->
                                    logger.info("Connection test for {}/{}: {}",
                                            connectorType, configName, result ? "SUCCESS" : "FAILED"));
                });
    }

    /**
     * Check if a scheduled sync should run based on polling interval
     */
    public Mono<Boolean> shouldRunScheduledSync(ConnectorConfig config) {
        return Mono.fromCallable(() -> {
            JsonNode configNode = config.getConfiguration();
            int pollingIntervalMinutes = configNode.path("polling_interval_minutes").asInt(30);

            String configKey = getConfigKey(config);
            LocalDateTime lastSync = lastSyncTimes.get(configKey);

            if (lastSync == null) {
                return true; // Never synced before
            }

            LocalDateTime nextSyncTime = lastSync.plusMinutes(pollingIntervalMinutes);
            return LocalDateTime.now().isAfter(nextSyncTime);
        });
    }

    /**
     * Get all available connector types
     */
    public Map<String, DataConnector> getAvailableConnectors() {
        return Map.copyOf(connectors);
    }

    /**
     * Get connector-specific metrics
     */
    public Mono<Map<String, Object>> getConnectorMetrics(String connectorType, String configName) {
        return configService.getConfiguration(connectorType, configName)
                .flatMap(config -> {
                    DataConnector connector = connectors.get(connectorType);
                    if (connector == null) {
                        return Mono.error(new IllegalArgumentException(
                                "No connector found for type: " + connectorType));
                    }

                    return Mono.zip(
                            connector.getMetrics(config),
                            connector.getRateLimitStatus(config),
                            jobService.getJobsForConnector(config.getId()).collectList()
                    ).map(tuple -> Map.of(
                            "metrics", tuple.getT1(),
                            "rateLimit", tuple.getT2(),
                            "recentJobs", tuple.getT3()
                    ));
                });
    }

    private Mono<Void> checkConcurrentJobsLimit() {
        return jobService.getRunningJobs()
                .count()
                .flatMap(runningCount -> {
                    if (runningCount >= maxConcurrentJobs) {
                        return Mono.error(new IllegalStateException(
                                "Maximum concurrent jobs limit reached: " + runningCount + "/" + maxConcurrentJobs));
                    }
                    return Mono.empty();
                });
    }

    private String getConfigKey(ConnectorConfig config) {
        return config.getConnectorType() + "/" + config.getName();
    }
}