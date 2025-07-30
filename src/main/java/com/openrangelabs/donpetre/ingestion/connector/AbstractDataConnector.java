package com.openrangelabs.donpetre.ingestion.connector;

import com.openrangelabs.donpetre.ingestion.entity.ConnectorConfig;
import com.openrangelabs.donpetre.ingestion.model.ConnectorMetrics;
import com.openrangelabs.donpetre.ingestion.model.KnowledgeItem;
import com.openrangelabs.donpetre.ingestion.model.SyncContext;
import com.openrangelabs.donpetre.ingestion.model.SyncResult;
import com.openrangelabs.donpetre.ingestion.service.CredentialService;
import com.openrangelabs.donpetre.ingestion.service.IngestionJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Abstract base class for data connectors
 * Provides common functionality and patterns
 */
public abstract class AbstractDataConnector implements DataConnector {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    protected CredentialService credentialService;

    @Autowired
    protected IngestionJobService ingestionJobService;

    private final AtomicLong processedItems = new AtomicLong(0);
    private final AtomicLong failedItems = new AtomicLong(0);

    @Override
    public boolean isEnabled() {
        // Override in specific connectors if needed
        return true;
    }

    @Override
    public Mono<SyncResult> performSync(ConnectorConfig config) {
        return validateConfiguration(config)
                .then(ingestionJobService.createJob(config.getId(), "full_sync"))
                .flatMap(job -> {
                    job.start();
                    return ingestionJobService.saveJob(job)
                            .then(doFullSync(config))
                            .doOnNext(result -> {
                                job.setItemsProcessed(result.getProcessedCount());
                                job.setItemsFailed(result.getFailedCount());
                                job.complete();
                            })
                            .doOnError(error -> {
                                job.fail(error.getMessage());
                                logger.error("Full sync failed for connector {}: {}",
                                        getConnectorType(), error.getMessage(), error);
                            })
                            .doFinally(signal -> ingestionJobService.saveJob(job).subscribe());
                });
    }

    @Override
    public Mono<SyncResult> performIncrementalSync(ConnectorConfig config, String lastSyncCursor) {
        return validateConfiguration(config)
                .then(ingestionJobService.createJob(config.getId(), "incremental"))
                .flatMap(job -> {
                    job.start();
                    job.setLastSyncCursor(lastSyncCursor);
                    return ingestionJobService.saveJob(job)
                            .then(doIncrementalSync(config, lastSyncCursor))
                            .doOnNext(result -> {
                                job.setItemsProcessed(result.getProcessedCount());
                                job.setItemsFailed(result.getFailedCount());
                                job.setLastSyncCursor(result.getNextCursor());
                                job.complete();
                            })
                            .doOnError(error -> {
                                job.fail(error.getMessage());
                                logger.error("Incremental sync failed for connector {}: {}",
                                        getConnectorType(), error.getMessage(), error);
                            })
                            .doFinally(signal -> ingestionJobService.saveJob(job).subscribe());
                });
    }

    @Override
    public Mono<ConnectorMetrics> getMetrics(ConnectorConfig config) {
        return Mono.fromCallable(() -> new ConnectorMetrics(
                getConnectorType(),
                processedItems.get(),
                failedItems.get(),
                LocalDateTime.now()
        ));
    }

    /**
     * Template method for full synchronization
     */
    protected abstract Mono<SyncResult> doFullSync(ConnectorConfig config);

    /**
     * Template method for incremental synchronization
     */
    protected abstract Mono<SyncResult> doIncrementalSync(ConnectorConfig config, String lastSyncCursor);

    /**
     * Helper method to process items with error handling
     */
    protected Flux<KnowledgeItem> processItemsWithErrorHandling(Flux<KnowledgeItem> items) {
        return items
                .doOnNext(item -> processedItems.incrementAndGet())
                .onErrorContinue((error, item) -> {
                    failedItems.incrementAndGet();
                    logger.warn("Failed to process item: {}", error.getMessage(), error);
                });
    }
}