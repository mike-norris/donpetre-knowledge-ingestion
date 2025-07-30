package com.openrangelabs.donpetre.ingestion.connector;

import com.openrangelabs.donpetre.ingestion.entity.ConnectorConfig;
import com.openrangelabs.donpetre.ingestion.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Base interface for all data connectors
 * Provides a unified API for different external data sources
 */
public interface DataConnector {

    /**
     * Get the connector type identifier
     */
    String getConnectorType();

    /**
     * Check if this connector is enabled
     */
    boolean isEnabled();

    /**
     * Validate connector configuration
     */
    Mono<Void> validateConfiguration(ConnectorConfig config);

    /**
     * Fetch data from the external source
     */
    Flux<KnowledgeItem> fetchData(ConnectorConfig config, SyncContext context);

    /**
     * Perform a full synchronization
     */
    Mono<SyncResult> performSync(ConnectorConfig config);

    /**
     * Perform an incremental synchronization
     */
    Mono<SyncResult> performIncrementalSync(ConnectorConfig config, String lastSyncCursor);

    /**
     * Test the connection to the external service
     */
    Mono<Boolean> testConnection(ConnectorConfig config);

    /**
     * Get the current rate limit status
     */
    Mono<RateLimitStatus> getRateLimitStatus(ConnectorConfig config);

    /**
     * Get connector-specific metrics
     */
    Mono<ConnectorMetrics> getMetrics(ConnectorConfig config);
}