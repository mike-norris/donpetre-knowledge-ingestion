package com.openrangelabs.donpetre.ingestion.service;

import com.openrangelabs.donpetre.ingestion.entity.IngestionJob;
import com.openrangelabs.donpetre.ingestion.repository.IngestionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing ingestion jobs
 */
@Service
public class IngestionJobService {

    private static final Logger logger = LoggerFactory.getLogger(IngestionJobService.class);

    private final IngestionJobRepository repository;

    @Autowired
    public IngestionJobService(IngestionJobRepository repository) {
        this.repository = repository;
    }

    /**
     * Create a new ingestion job
     */
    public Mono<IngestionJob> createJob(UUID connectorConfigId, String jobType) {
        IngestionJob job = new IngestionJob(connectorConfigId, jobType);
        return repository.save(job)
                .doOnSuccess(saved -> logger.info("Created ingestion job: {} for connector: {}",
                        saved.getId(), connectorConfigId));
    }

    /**
     * Save job state
     */
    public Mono<IngestionJob> saveJob(IngestionJob job) {
        return repository.save(job);
    }

    /**
     * Get job by ID
     */
    public Mono<IngestionJob> getJob(UUID jobId) {
        return repository.findById(jobId);
    }

    /**
     * Get jobs for connector
     */
    public Flux<IngestionJob> getJobsForConnector(UUID connectorConfigId) {
        return repository.findByConnectorConfigIdOrderByStartedAtDesc(connectorConfigId);
    }

    /**
     * Get running jobs
     */
    public Flux<IngestionJob> getRunningJobs() {
        return repository.findRunningJobs();
    }

    /**
     * Get latest job for connector
     */
    public Mono<IngestionJob> getLatestJob(UUID connectorConfigId) {
        return repository.findLatestByConnectorConfigId(connectorConfigId);
    }

    /**
     * Get latest successful job for connector
     */
    public Mono<IngestionJob> getLatestSuccessfulJob(UUID connectorConfigId) {
        return repository.findLatestSuccessfulByConnectorConfigId(connectorConfigId);
    }

    /**
     * Clean up old completed jobs
     */
    public Mono<Integer> cleanupOldJobs(int daysToKeep) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(daysToKeep);
        return repository.deleteOldJobs(threshold)
                .doOnSuccess(count -> logger.info("Cleaned up {} old ingestion jobs", count));
    }

    /**
     * Get job performance metrics
     */
    public Flux<IngestionJobRepository.JobPerformanceMetrics> getPerformanceMetrics(int hoursBack) {
        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);
        return repository.getJobPerformanceMetrics(since);
    }

    /**
     * Get job statistics by connector type
     */
    public Flux<IngestionJobRepository.JobStatsByConnectorType> getJobStatsByConnectorType() {
        return repository.getJobStatsByConnectorType();
    }

    /**
     * Find long-running jobs
     */
    public Flux<IngestionJob> getLongRunningJobs(int minutesThreshold) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutesThreshold);
        return repository.findLongRunningJobs(threshold);
    }
}