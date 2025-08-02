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
        logger.debug("Starting scheduled ingestion check");

        configService.getEnabledConfigurations()
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
}