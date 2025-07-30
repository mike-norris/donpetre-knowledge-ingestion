package com.openrangelabs.donpetre.ingestion.repository;

import com.openrangelabs.donpetre.ingestion.entity.IngestionJob;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Repository for ingestion job management
 */
@Repository
public interface IngestionJobRepository extends R2dbcRepository<IngestionJob, UUID> {

    /**
     * Find jobs by connector config ID
     */
    Flux<IngestionJob> findByConnectorConfigIdOrderByStartedAtDesc(UUID connectorConfigId);

    /**
     * Find jobs by status
     */
    Flux<IngestionJob> findByStatus(String status);

    /**
     * Find running jobs
     */
    @Query("SELECT * FROM ingestion_jobs WHERE status = 'RUNNING' ORDER BY started_at ASC")
    Flux<IngestionJob> findRunningJobs();

    /**
     * Find jobs by connector config ID and status
     */
    Flux<IngestionJob> findByConnectorConfigIdAndStatus(UUID connectorConfigId, String status);

    /**
     * Find latest job by connector config ID
     */
    @Query("""
        SELECT * FROM ingestion_jobs 
        WHERE connector_config_id = :configId 
        ORDER BY started_at DESC 
        LIMIT 1
        """)
    Mono<IngestionJob> findLatestByConnectorConfigId(@Param("configId") UUID connectorConfigId);

    /**
     * Find latest successful job by connector config ID
     */
    @Query("""
        SELECT * FROM ingestion_jobs 
        WHERE connector_config_id = :configId 
        AND status = 'COMPLETED'
        ORDER BY completed_at DESC 
        LIMIT 1
        """)
    Mono<IngestionJob> findLatestSuccessfulByConnectorConfigId(@Param("configId") UUID connectorConfigId);

    /**
     * Find jobs started after a specific time
     */
    @Query("SELECT * FROM ingestion_jobs WHERE started_at > :since ORDER BY started_at DESC")
    Flux<IngestionJob> findStartedSince(@Param("since") LocalDateTime since);

    /**
     * Find failed jobs within time range
     */
    @Query("""
        SELECT * FROM ingestion_jobs 
        WHERE status = 'FAILED' 
        AND started_at BETWEEN :startDate AND :endDate 
        ORDER BY started_at DESC
        """)
    Flux<IngestionJob> findFailedJobsInRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count jobs by status
     */
    @Query("SELECT COUNT(*) FROM ingestion_jobs WHERE status = :status")
    Mono<Long> countByStatus(@Param("status") String status);

    /**
     * Count jobs by connector config ID and status
     */
    @Query("""
        SELECT COUNT(*) FROM ingestion_jobs 
        WHERE connector_config_id = :configId AND status = :status
        """)
    Mono<Long> countByConnectorConfigIdAndStatus(
            @Param("configId") UUID connectorConfigId,
            @Param("status") String status);

    /**
     * Find long-running jobs (running for more than specified minutes)
     */
    @Query("""
        SELECT * FROM ingestion_jobs 
        WHERE status = 'RUNNING' 
        AND started_at < :threshold
        ORDER BY started_at ASC
        """)
    Flux<IngestionJob> findLongRunningJobs(@Param("threshold") LocalDateTime threshold);

    /**
     * Get job statistics by connector type
     */
    @Query("""
        SELECT 
            cc.connector_type,
            COUNT(ij.*) as total_jobs,
            COUNT(CASE WHEN ij.status = 'COMPLETED' THEN 1 END) as completed_jobs,
            COUNT(CASE WHEN ij.status = 'FAILED' THEN 1 END) as failed_jobs,
            COUNT(CASE WHEN ij.status = 'RUNNING' THEN 1 END) as running_jobs,
            AVG(ij.items_processed) as avg_items_processed,
            SUM(ij.items_processed) as total_items_processed,
            SUM(ij.items_failed) as total_items_failed
        FROM ingestion_jobs ij
        JOIN connector_configs cc ON ij.connector_config_id = cc.id
        GROUP BY cc.connector_type
        ORDER BY cc.connector_type
        """)
    Flux<JobStatsByConnectorType> getJobStatsByConnectorType();

    /**
     * Get recent job performance metrics
     */
    @Query("""
        SELECT 
            DATE_TRUNC('hour', started_at) as hour,
            COUNT(*) as job_count,
            COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as successful_jobs,
            COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as failed_jobs,
            AVG(items_processed) as avg_items_processed,
            AVG(EXTRACT(EPOCH FROM (completed_at - started_at))) as avg_duration_seconds
        FROM ingestion_jobs 
        WHERE started_at > :since 
        AND completed_at IS NOT NULL
        GROUP BY DATE_TRUNC('hour', started_at)
        ORDER BY hour DESC
        """)
    Flux<JobPerformanceMetrics> getJobPerformanceMetrics(@Param("since") LocalDateTime since);

    /**
     * Clean up old completed jobs
     */
    @Query("""
        DELETE FROM ingestion_jobs 
        WHERE status IN ('COMPLETED', 'FAILED') 
        AND completed_at < :threshold
        """)
    Mono<Integer> deleteOldJobs(@Param("threshold") LocalDateTime threshold);

    /**
     * Interface for job statistics by connector type
     */
    interface JobStatsByConnectorType {
        String getConnectorType();
        Long getTotalJobs();
        Long getCompletedJobs();
        Long getFailedJobs();
        Long getRunningJobs();
        Double getAvgItemsProcessed();
        Long getTotalItemsProcessed();
        Long getTotalItemsFailed();
    }

    /**
     * Interface for job performance metrics
     */
    interface JobPerformanceMetrics {
        LocalDateTime getHour();
        Long getJobCount();
        Long getSuccessfulJobs();
        Long getFailedJobs();
        Double getAvgItemsProcessed();
        Double getAvgDurationSeconds();
    }
}