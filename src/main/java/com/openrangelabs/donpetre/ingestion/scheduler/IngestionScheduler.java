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
                        results -> logger.debug("Completed scheduled ingestion check, {} syncs initiated", results.size()),
                        error -> logger.error("Error during scheduled ingestion", error)
                );
    }

    /**
     * Cleanup old completed jobs
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "${ingestion.jobs.cleanup.schedule:0 2 * * *}")
    public void cleanupOldJobs() {
        if (!cleanupEnabled) {
            logger.debug("Job cleanup is disabled");
            return;
        }

        logger.info("Starting cleanup of old ingestion jobs (retention: {} days)", retentionDays);

        jobService.cleanupOldJobs(retentionDays)
                .subscribe(
                        count -> logger.info("Cleaned up {} old ingestion jobs", count),
                        error -> logger.error("Error during job cleanup", error)
                );
    }

    /**
     * Check for expiring credentials
     * Runs weekly on Monday at 8 AM
     */
    @Scheduled(cron = "${ingestion.jobs.credential-check.schedule:0 8 * * MON}")
    public void checkExpiringCredentials() {
        if (!credentialCheckEnabled) {
            logger.debug("Credential expiration check is disabled");
            return;
        }

        logger.info("Checking for credentials expiring within {} days", expirationWarningDays);

        credentialService.getCredentialsExpiringSoon(expirationWarningDays)
                .collectList()
                .subscribe(
                        credentials -> {
                            if (!credentials.isEmpty()) {
                                logger.warn("Found {} credentials expiring soon:", credentials.size());
                                credentials.forEach(cred ->
                                        logger.warn("Credential {} expires at: {}", cred.getId(), cred.getExpiresAt())
                                );
                                // TODO: Send notifications/alerts
                            } else {
                                logger.info("No credentials expiring within {} days", expirationWarningDays);
                            }
                        },
                        error -> logger.error("Error checking expiring credentials", error)
                );
    }

    /**
     * Monitor long-running jobs
     * Runs every 15 minutes
     */
    @Scheduled(fixedDelay = 900000) // 15 minutes
    public void monitorLongRunningJobs() {
        logger.debug("Checking for long-running jobs");

        jobService.getLongRunningJobs(60) // Jobs running for more than 60 minutes
                .collectList()
                .subscribe(
                        longRunningJobs -> {
                            if (!longRunningJobs.isEmpty()) {
                                logger.warn("Found {} long-running jobs:", longRunningJobs.size());
                                longRunningJobs.forEach(job ->
                                        logger.warn("Job {} has been running since: {}", job.getId(), job.getStartedAt())
                                );
                                // TODO: Consider auto-termination or alerts
                            }
                        },
                        error -> logger.error("Error monitoring long-running jobs", error)
                );
    }
}