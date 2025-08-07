package com.openrangelabs.donpetre.ingestion.scheduler;

import com.openrangelabs.donpetre.ingestion.service.ConnectorConfigService;
import com.openrangelabs.donpetre.ingestion.service.CredentialService;
import com.openrangelabs.donpetre.ingestion.service.IngestionJobService;
import com.openrangelabs.donpetre.ingestion.service.IngestionOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
// FIXED: Add missing Mono import
import reactor.core.publisher.Mono;

/**
 * Scheduler for automated ingestion tasks
 */
@Component
public class IngestionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(IngestionScheduler.class);

    private final IngestionOrchestrationService orchestrationService;
    private final IngestionJobService jobService;
    private final CredentialService credentialService;
    private final ConnectorConfigService configService;

    @Value("${ingestion.jobs.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${ingestion.jobs.cleanup.retention-days:30}")
    private int retentionDays;

    @Value("${ingestion.jobs.credential-check.enabled:true}")
    private boolean credentialCheckEnabled;

    @Value("${ingestion.jobs.credential-check.expiration-warning-days:7}")
    private int expirationWarningDays;

    @Value("${ingestion.scheduling.enabled:true}")
    private boolean scheduledSyncEnabled;

    @Autowired
    public IngestionScheduler(
            IngestionOrchestrationService orchestrationService,
            IngestionJobService jobService,
            CredentialService credentialService,
            ConnectorConfigService configService) {
        this.orchestrationService = orchestrationService;
        this.jobService = jobService;
        this.credentialService = credentialService;
        this.configService = configService;
    }

    /**
     * Scheduled ingestion for all enabled connectors
     * Runs every 5 minutes
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void scheduledIngestion() {
        if (!scheduledSyncEnabled) {
            logger.debug("Scheduled ingestion is disabled");
            return;
        }

        logger.debug("Starting scheduled ingestion check");

        configService.findConfigurationsForScheduledSync()
                .flatMap(config ->
                        orchestrationService.shouldRunScheduledSync(config)
                                .filter(shouldRun -> shouldRun)
                                .flatMap(shouldRun ->
                                        orchestrationService.scheduleIncrementalSync(config)
                                                .doOnSuccess(result -> logger.info("Scheduled sync for connector: {} - {}",
                                                        config.getConnectorType(), config.getName()))
                                                .doOnError(error -> logger.error("Failed to schedule sync for connector: {} - {}",
                                                        config.getConnectorType(), config.getName(), error))
                                                .onErrorResume(error -> Mono.empty())
                                )
                )
                .collectList()
                .subscribe(
                        results -> logger.debug("Completed scheduled ingestion check: {} connectors processed", results.size()),
                        error -> logger.error("Error during scheduled ingestion: {}", error.getMessage())
                );
    }

    /**
     * Run scheduled ingestion (called by test)
     */
    public void runScheduledIngestion() {
        if (!scheduledSyncEnabled) {
            logger.debug("Scheduled sync is disabled");
            return;
        }

        logger.debug("Running scheduled ingestion check");

        configService.findConfigurationsForScheduledSync()
                .flatMap(config ->
                        orchestrationService.shouldRunScheduledSync(config)
                                .filter(shouldRun -> shouldRun)
                                .flatMap(shouldRun ->
                                        orchestrationService.scheduleIncrementalSync(config)
                                                .doOnSuccess(result -> logger.info("Scheduled sync for connector: {} - {}",
                                                        config.getConnectorType(), config.getName()))
                                                .doOnError(error -> logger.error("Failed to schedule sync for connector: {} - {}",
                                                        config.getConnectorType(), config.getName(), error))
                                                .onErrorResume(error -> Mono.empty())
                                )
                )
                .collectList()
                .subscribe(
                        results -> logger.debug("Completed scheduled ingestion check: {} connectors processed", results.size()),
                        error -> logger.error("Error during scheduled ingestion: {}", error.getMessage())
                );
    }

    /**
     * Clean up old completed jobs
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldJobs() {
        if (!cleanupEnabled) {
            logger.debug("Job cleanup is disabled");
            return;
        }

        logger.info("Starting cleanup of jobs older than {} days", retentionDays);

        jobService.cleanupOldJobs(retentionDays)
                .subscribe(
                        deletedCount -> logger.info("Cleaned up {} old jobs", deletedCount),
                        error -> logger.error("Error during job cleanup: {}", error.getMessage())
                );
    }

    /**
     * Check for expiring credentials
     * Runs daily at 8 AM
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void checkExpiringCredentials() {
        if (!credentialCheckEnabled) {
            logger.debug("Credential expiration check is disabled");
            return;
        }

        logger.info("Checking for credentials expiring within {} days", expirationWarningDays);

        credentialService.getCredentialsExpiringSoon(expirationWarningDays)
                .collectList()
                .subscribe(
                        expiringCreds -> {
                            if (!expiringCreds.isEmpty()) {
                                logger.warn("Found {} credentials expiring soon", expiringCreds.size());
                                // TODO: Send notifications or alerts
                            } else {
                                logger.info("No credentials expiring soon");
                            }
                        },
                        error -> logger.error("Error checking expiring credentials: {}", error.getMessage())
                );
    }

    /**
     * Run health check for the scheduler
     */
    public void runHealthCheck() {
        logger.debug("Running scheduler health check");
        
        // Check if services are available
        try {
            configService.getTotalConfigurationCount()
                    .doOnSuccess(count -> logger.debug("Health check: Found {} configurations", count))
                    .doOnError(error -> logger.error("Health check failed: {}", error.getMessage()))
                    .subscribe();
                    
            jobService.getJobStatistics()
                    .doOnSuccess(stats -> logger.debug("Health check: Job statistics - Total: {}, Running: {}", 
                            stats.total(), stats.running()))
                    .doOnError(error -> logger.error("Health check job statistics failed: {}", error.getMessage()))
                    .subscribe();
                    
            logger.info("Scheduler health check completed successfully");
        } catch (Exception e) {
            logger.error("Scheduler health check failed with exception: {}", e.getMessage(), e);
        }
    }

    /**
     * Run maintenance tasks
     */
    public void runMaintenanceTasks() {
        logger.info("Running scheduler maintenance tasks");
        
        try {
            // Clean up old jobs if enabled
            if (cleanupEnabled) {
                jobService.cleanupOldJobs(retentionDays)
                        .doOnSuccess(count -> logger.info("Maintenance: Cleaned up {} old jobs", count))
                        .doOnError(error -> logger.error("Maintenance job cleanup failed: {}", error.getMessage()))
                        .subscribe();
            }
            
            // Check for expired credentials
            credentialService.getExpiredCredentials()
                    .collectList()
                    .doOnSuccess(expiredCreds -> {
                        if (!expiredCreds.isEmpty()) {
                            logger.warn("Maintenance: Found {} expired credentials", expiredCreds.size());
                            // Could add logic to deactivate expired credentials
                        }
                    })
                    .doOnError(error -> logger.error("Maintenance expired credentials check failed: {}", error.getMessage()))
                    .subscribe();
                    
            logger.info("Scheduler maintenance tasks completed successfully");
        } catch (Exception e) {
            logger.error("Scheduler maintenance tasks failed with exception: {}", e.getMessage(), e);
        }
    }
}