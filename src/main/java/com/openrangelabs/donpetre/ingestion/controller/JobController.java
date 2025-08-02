package com.openrangelabs.donpetre.ingestion.controller;

import com.openrangelabs.donpetre.ingestion.entity.IngestionJob;
import com.openrangelabs.donpetre.ingestion.service.IngestionJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for ingestion job management and monitoring
 * Provides comprehensive job tracking, performance metrics, and operational insights
 */
@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "${open-range-labs.donpetre.security.cors.allowed-origins}")
@PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
public class JobController {

    private final IngestionJobService jobService;

    @Autowired
    public JobController(IngestionJobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Get specific job by ID
     *
     * This endpoint retrieves detailed information about a specific ingestion job,
     * including its status, processing statistics, and execution timeline.
     *
     * @param jobId The unique identifier of the job
     * @return Complete job information including metadata and statistics
     */
    @GetMapping("/{jobId}")
    public Mono<ResponseEntity<Map<String, Object>>> getJob(@PathVariable UUID jobId) {
        return jobService.getJob(jobId)
                .map(job -> {
                    Map<String, Object> response = new HashMap<>();

                    // FIXED: Use correct IngestionJob field names
                    response.put("id", job.getId());
                    response.put("connectorConfigId", job.getConnectorConfigId());
                    response.put("jobType", job.getJobType());
                    response.put("status", job.getStatus());
                    response.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
                    response.put("completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null);
                    response.put("lastSyncCursor", job.getLastSyncCursor());
                    response.put("errorMessage", job.getErrorMessage());
                    response.put("metadata", job.getMetadata());

                    // FIXED: Use correct method names
                    response.put("itemsProcessed", job.getItemsProcessed());  // was getTotalRecords()
                    response.put("itemsFailed", job.getItemsFailed());        // was getProcessedRecords()
                    response.put("totalItems", job.getTotalItems());

                    // FIXED: Handle Duration object properly
                    response.put("duration", job.getDuration().toString());
                    response.put("durationMinutes", job.getDurationMinutes());

                    // Status flags
                    response.put("isRunning", job.isRunning());
                    response.put("isCompleted", job.isCompleted());
                    response.put("isPending", job.isPending());
                    response.put("isFailed", job.isFailed());
                    response.put("isSuccessful", job.isSuccessful());

                    // Performance metrics
                    response.put("successRate", job.getSuccessRate());
                    response.put("failureRate", job.getFailureRate());
                    response.put("hasErrors", job.hasErrors());

                    return ResponseEntity.ok(response);
                })
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * Get all jobs for a specific connector configuration
     *
     * This endpoint returns the job history for a particular connector,
     * ordered by most recent first. Useful for monitoring connector health
     * and troubleshooting specific connector issues.
     *
     * @param connectorConfigId The connector configuration ID
     * @return List of jobs for the specified connector, ordered by recency
     */
    @GetMapping("/connector/{connectorConfigId}")
    public Flux<Map<String, Object>> getJobsForConnector(@PathVariable UUID connectorConfigId) {
        return jobService.getJobsForConnector(connectorConfigId)
                .map(this::createJobSummaryMap);
    }

    /**
     * Get all currently running jobs
     *
     * This endpoint provides real-time visibility into active ingestion jobs.
     * Essential for monitoring system load and identifying stuck or long-running jobs.
     *
     * @return List of all jobs currently in RUNNING status
     */
    @GetMapping("/running")
    public Flux<Map<String, Object>> getRunningJobs() {
        return jobService.getRunningJobs()
                .map(job -> {
                    Map<String, Object> response = new HashMap<>();

                    long runningMinutes = job.getStartedAt() != null ?
                            Duration.between(job.getStartedAt(), LocalDateTime.now()).toMinutes() : 0;

                    response.put("id", job.getId());
                    response.put("connectorConfigId", job.getConnectorConfigId());
                    response.put("jobType", job.getJobType());
                    response.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
                    response.put("runningMinutes", runningMinutes);
                    response.put("itemsProcessed", job.getItemsProcessed());
                    response.put("itemsFailed", job.getItemsFailed());
                    response.put("currentRate", calculateCurrentProcessingRate(job));
                    response.put("status", "RUNNING");
                    response.put("isLongRunning", runningMinutes > 60);

                    return response;
                });
    }

    /**
     * Get latest job for a specific connector
     *
     * This endpoint returns the most recent job for a connector, regardless of status.
     * Useful for quick status checks and dashboard displays.
     *
     * @param connectorConfigId The connector configuration ID
     * @return The most recent job for the specified connector
     */
    @GetMapping("/connector/{connectorConfigId}/latest")
    public Mono<ResponseEntity<Map<String, Object>>> getLatestJob(@PathVariable UUID connectorConfigId) {
        return jobService.getLatestJob(connectorConfigId)
                .map(job -> {
                    Map<String, Object> response = new HashMap<>();

                    response.put("id", job.getId());
                    response.put("jobType", job.getJobType());
                    response.put("status", job.getStatus());
                    response.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
                    response.put("completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null);
                    response.put("itemsProcessed", job.getItemsProcessed());
                    response.put("itemsFailed", job.getItemsFailed());
                    response.put("lastSyncCursor", job.getLastSyncCursor());
                    response.put("duration", job.getDuration().toString());
                    response.put("successRate", job.getSuccessRate());
                    response.put("recommendation", getJobRecommendation(job));

                    return ResponseEntity.ok(response);
                })
                // FIXED: Proper generic typing for ResponseEntity
                .switchIfEmpty(Mono.just(ResponseEntity.<Map<String, Object>>notFound().build()));
    }

    /**
     * Get job performance metrics over time
     *
     * This endpoint provides aggregated performance data for analyzing ingestion
     * trends, identifying bottlenecks, and capacity planning.
     *
     * @return Performance metrics aggregated across all jobs
     */
    @GetMapping("/metrics/performance")
    public Mono<ResponseEntity<Map<String, Object>>> getPerformanceMetrics() {
        return jobService.getRunningJobs()
                .collectList()
                .flatMap(runningJobs ->
                        // Get all jobs for comprehensive metrics
                        jobService.getJobsForConnector(null) // This might need adjustment based on your service
                                .collectList()
                                .map(allJobs -> createJobMetricsMap(allJobs, runningJobs))
                )
                .map(metrics -> ResponseEntity.ok(metrics))
                .onErrorReturn(ResponseEntity.status(500)
                        .body(createErrorResponseMap("Failed to retrieve performance metrics")));
    }

    // FIXED: Helper methods with correct return types and method calls

    /**
     * Creates a response map for job operations with all available IngestionJob fields
     */
    private Map<String, Object> createJobResponseMap(IngestionJob job) {
        Map<String, Object> response = new HashMap<>();

        // Basic job information
        response.put("id", job.getId());
        response.put("connectorConfigId", job.getConnectorConfigId());
        response.put("jobType", job.getJobType());
        response.put("status", job.getStatus());
        response.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
        response.put("completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null);
        response.put("lastSyncCursor", job.getLastSyncCursor());
        response.put("errorMessage", job.getErrorMessage());
        response.put("metadata", job.getMetadata());

        // Processing statistics (correct field names)
        response.put("itemsProcessed", job.getItemsProcessed());
        response.put("itemsFailed", job.getItemsFailed());
        response.put("totalItems", job.getTotalItems());

        // Duration and timing
        response.put("duration", job.getDuration().toString());
        response.put("durationMinutes", job.getDurationMinutes());

        // Status flags
        response.put("isRunning", job.isRunning());
        response.put("isCompleted", job.isCompleted());
        response.put("isPending", job.isPending());
        response.put("isFailed", job.isFailed());
        response.put("isSuccessful", job.isSuccessful());

        // Performance metrics
        response.put("successRate", job.getSuccessRate());
        response.put("failureRate", job.getFailureRate());
        response.put("hasErrors", job.hasErrors());

        return response;
    }

    /**
     * Creates a simplified response map for job lists
     */
    private Map<String, Object> createJobSummaryMap(IngestionJob job) {
        Map<String, Object> response = new HashMap<>();

        response.put("id", job.getId());
        response.put("connectorConfigId", job.getConnectorConfigId());
        response.put("jobType", job.getJobType());
        response.put("status", job.getStatus());
        response.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
        response.put("completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null);
        response.put("itemsProcessed", job.getItemsProcessed());
        response.put("itemsFailed", job.getItemsFailed());
        response.put("duration", job.getDuration().toString());
        response.put("isRunning", job.isRunning());
        response.put("isCompleted", job.isCompleted());
        response.put("successRate", job.getSuccessRate());
        response.put("status_color", getStatusColor(job.getStatus()));
        response.put("hasErrors", job.hasErrors());

        return response;
    }

    /**
     * Creates a metrics response map for performance statistics
     */
    private Map<String, Object> createJobMetricsMap(List<IngestionJob> allJobs, List<IngestionJob> runningJobs) {
        Map<String, Object> response = new HashMap<>();

        if (allJobs.isEmpty()) {
            response.put("status", "success");
            response.put("totalJobs", 0L);
            response.put("completedJobs", 0L);
            response.put("failedJobs", 0L);
            response.put("runningJobs", 0L);
            response.put("averageDuration", 0.0);
            response.put("successRate", 100.0);
            response.put("errorRate", 0.0);
            response.put("timestamp", LocalDateTime.now().toString());
            response.put("isHealthy", true);
            return response;
        }

        // Calculate metrics from actual jobs
        long totalJobs = allJobs.size();
        long completedJobs = allJobs.stream().mapToLong(job -> job.isSuccessful() ? 1 : 0).sum();
        long failedJobs = allJobs.stream().mapToLong(job -> job.isFailed() ? 1 : 0).sum();
        long currentlyRunning = runningJobs.size();

        double averageDuration = allJobs.stream()
                .filter(IngestionJob::isCompleted)
                .mapToDouble(IngestionJob::getDurationMinutes)
                .average()
                .orElse(0.0);

        double successRate = totalJobs > 0 ? (completedJobs * 100.0) / totalJobs : 100.0;
        double errorRate = totalJobs > 0 ? (failedJobs * 100.0) / totalJobs : 0.0;

        response.put("status", "success");
        response.put("totalJobs", totalJobs);
        response.put("completedJobs", completedJobs);
        response.put("failedJobs", failedJobs);
        response.put("runningJobs", currentlyRunning);
        response.put("averageDuration", averageDuration);
        response.put("successRate", successRate);
        response.put("errorRate", errorRate);
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("isHealthy", errorRate < 50.0); // Healthy if less than 50% error rate

        return response;
    }

    /**
     * Creates an error response map
     */
    private Map<String, Object> createErrorResponseMap(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    // FIXED: Utility methods with safe calculations

    private double calculateCurrentProcessingRate(IngestionJob job) {
        if (job.getStartedAt() == null || job.getItemsProcessed() == null) {
            return 0.0;
        }

        long minutesRunning = Duration.between(job.getStartedAt(), LocalDateTime.now()).toMinutes();
        if (minutesRunning <= 0) {
            return 0.0;
        }

        return (double) job.getItemsProcessed() / minutesRunning;
    }

    private String getStatusColor(String status) {
        return switch (status.toUpperCase()) {
            case "COMPLETED" -> "green";
            case "RUNNING" -> "blue";
            case "FAILED" -> "red";
            case "CANCELLED" -> "orange";
            case "PENDING" -> "yellow";
            default -> "gray";
        };
    }

    private String getJobRecommendation(IngestionJob job) {
        if (job.isFailed() && job.hasErrors()) {
            return "Check error message and retry sync";
        } else if (job.isSuccessful() && job.getSuccessRate() < 95.0) {
            return "Some items failed - review logs for issues";
        } else if (job.isRunning() && job.getDurationMinutes() > 120) {
            return "Long-running job - consider monitoring";
        } else if (job.isSuccessful()) {
            return "Sync completed successfully";
        } else {
            return "Monitor job progress";
        }
    }
}