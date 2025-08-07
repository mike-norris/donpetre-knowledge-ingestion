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

    /**
     * Update job status with optional error message
     */
    public Mono<IngestionJob> updateJobStatus(UUID jobId, String status, String errorMessage) {
        logger.debug("Updating job status: {} to {}", jobId, status);
        return repository.findById(jobId)
                .flatMap(job -> {
                    job.setStatus(status);
                    
                    if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                        job.setErrorMessage(errorMessage);
                    }
                    
                    if ("COMPLETED".equals(status)) {
                        job.setCompletedAt(LocalDateTime.now());
                    } else if ("FAILED".equals(status)) {
                        job.setCompletedAt(LocalDateTime.now());
                    }
                    
                    return repository.save(job);
                })
                .doOnSuccess(job -> logger.debug("Updated job {} status to {}", jobId, status))
                .switchIfEmpty(Mono.error(new RuntimeException("Job not found: " + jobId)));
    }

    /**
     * Mark job as completed with metrics
     */
    public Mono<IngestionJob> markJobCompleted(UUID jobId, int processedItems, int failedItems, String nextCursor) {
        logger.debug("Marking job {} as completed: processed={}, failed={}", jobId, processedItems, failedItems);
        return repository.findById(jobId)
                .flatMap(job -> {
                    job.setStatus("COMPLETED");
                    job.setCompletedAt(LocalDateTime.now());
                    job.setItemsProcessed(processedItems);
                    job.setItemsFailed(failedItems);
                    
                    if (nextCursor != null) {
                        job.setLastSyncCursor(nextCursor);
                    }
                    
                    return repository.save(job);
                })
                .doOnSuccess(job -> logger.info("Job {} completed: processed {}, failed {}", jobId, processedItems, failedItems))
                .switchIfEmpty(Mono.error(new RuntimeException("Job not found: " + jobId)));
    }

    /**
     * Mark job as failed with error message
     */
    public Mono<IngestionJob> markJobFailed(UUID jobId, String errorMessage) {
        logger.debug("Marking job {} as failed: {}", jobId, errorMessage);
        return repository.findById(jobId)
                .flatMap(job -> {
                    job.setStatus("FAILED");
                    job.setCompletedAt(LocalDateTime.now());
                    job.setErrorMessage(errorMessage);
                    
                    return repository.save(job);
                })
                .doOnSuccess(job -> logger.warn("Job {} failed: {}", jobId, errorMessage))
                .switchIfEmpty(Mono.error(new RuntimeException("Job not found: " + jobId)));
    }

    /**
     * Delete old jobs before a specific date
     */
    public Mono<Long> deleteOldJobs(LocalDateTime cutoffDate) {
        logger.debug("Deleting jobs older than {}", cutoffDate);
        return repository.deleteByCreatedAtBefore(cutoffDate)
                .map(Integer::longValue)
                .doOnSuccess(count -> logger.info("Deleted {} old jobs", count));
    }

    /**
     * Get jobs by connector config ordered by created date desc
     */
    public Flux<IngestionJob> findByConnectorConfigIdOrderByCreatedAtDesc(UUID connectorConfigId) {
        return repository.findByConnectorConfigIdOrderByCreatedAtDesc(connectorConfigId);
    }

    /**
     * Find first job by connector config and status ordered by created date desc
     */
    public Mono<IngestionJob> findFirstByConnectorConfigIdAndStatusOrderByCreatedAtDesc(UUID connectorConfigId, String status) {
        return repository.findFirstByConnectorConfigIdAndStatusOrderByCreatedAtDesc(connectorConfigId, status);
    }

    /**
     * Count recent jobs by connector and status
     */
    public Mono<Long> countRecentJobsByConnectorAndStatus(UUID connectorConfigId, String status, LocalDateTime since) {
        return repository.countByConnectorConfigIdAndStatusAndCreatedAtAfter(connectorConfigId, status, since);
    }

    /**
     * Get job statistics for monitoring
     */
    public Mono<JobStatistics> getJobStatistics() {
        return Mono.zip(
                repository.countByStatus("RUNNING"),
                repository.countByStatus("COMPLETED"),
                repository.countByStatus("FAILED"),
                repository.count()
        ).map(tuple -> new JobStatistics(
                tuple.getT1(),
                tuple.getT2(), 
                tuple.getT3(),
                tuple.getT4()
        ));
    }

    /**
     * Simple statistics holder
     */
    public record JobStatistics(Long running, Long completed, Long failed, Long total) {}
}