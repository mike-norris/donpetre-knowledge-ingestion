// src/main/java/com/openrangelabs/donpetre/ingestion/controller/JobController.java
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
                .map(job -> ResponseEntity.ok(Map.of(
                        "id", job.getId(),
                        "connectorConfigId", job.getConnectorConfigId(),
                        "jobType", job.getJobType(),
                        "status", job.getStatus(),
                        "startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null,
                        "completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null,
                        "itemsProcessed", job.getItemsProcessed(),
                        "itemsFailed", job.getItemsFailed(),
                        "lastSyncCursor", job.getLastSyncCursor(),
                        "errorMessage", job.getErrorMessage(),
                        "metadata", job.getMetadata(),
                        "duration", calculateDuration(job),
                        "successRate", calculateSuccessRate(job),
                        "isRunning", job.isRunning(),
                        "isCompleted", job.isCompleted()
                )))
                .defaultIfEmpty(ResponseEntity.notFound().build());
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
                .map(job -> Map.of(
                        "id", job.getId(),
                        "jobType", job.getJobType(),
                        "status", job.getStatus(),
                        "startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null,
                        "completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null,
                        "itemsProcessed", job.getItemsProcessed(),
                        "itemsFailed", job.getItemsFailed(),
                        "duration", calculateDuration(job),
                        "successRate", calculateSuccessRate(job),
                        "status_color", getStatusColor(job.getStatus()),
                        "hasErrors", job.getErrorMessage() != null
                ));
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
                    long runningMinutes = job.getStartedAt() != null ?
                            Duration.between(job.getStartedAt(), LocalDateTime.now()).toMinutes() : 0;

                    return Map.of(
                            "id", job.getId(),
                            "connectorConfigId", job.getConnectorConfigId(),
                            "jobType", job.getJobType(),
                            "startedAt", job.getStartedAt().toString(),
                            "runningMinutes", runningMinutes,
                            "itemsProcessed", job.getItemsProcessed(),
                            "itemsFailed", job.getItemsFailed(),
                            "currentRate", calculateCurrentProcessingRate(job),
                            "status", "RUNNING",
                            "isLongRunning", runningMinutes > 60
                    );
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
                .map(job -> ResponseEntity.ok(Map.of(
                        "id", job.getId(),
                        "jobType", job.getJobType(),
                        "status", job.getStatus(),
                        "startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null,
                        "completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null,
                        "itemsProcessed", job.getItemsProcessed(),
                        "itemsFailed", job.getItemsFailed(),
                        "lastSyncCursor", job.getLastSyncCursor(),
                        "duration", calculateDuration(job),
                        "successRate", calculateSuccessRate(job),
                        "recommendation", getJobRecommendation(job)
                )))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Get job performance metrics over time
     *
     * This endpoint provides aggregated performance data for analyzing ingestion
     * trends, identifying bottlenecks, and capacity planning.
     *
     * @param hoursBack Number of hours to look back for metrics (default: 24)
     * @return Hourly aggregated performance metrics
     */
    @GetMapping("/metrics/performance")
    public Flux<Map<String, Object>> getPerformanceMetrics(
            @RequestParam(defaultValue = "24") int hoursBack) {

        return jobService.getPerformanceMetrics(hoursBack)
                .map(metrics -> Map.of(
                        "hour", metrics.getHour().toString(),
                        "jobCount", metrics.getJobCount(),
                        "successfulJobs", metrics.getSuccessfulJobs(),
                        "failedJobs", metrics.getFailedJobs(),
                        "avgItemsProcessed", metrics.getAvgItemsProcessed() != null ?
                                Math.round(metrics.getAvgItemsProcessed() * 100.0) / 100.0 : 0,
                        "avgDurationSeconds", metrics.getAvgDurationSeconds() != null ?
                                Math.round(metrics.getAvgDurationSeconds() * 100.0) / 100.0 : 0,
                        "successRate", calculateHourlySuccessRate(metrics),
                        "throughputPerMinute", calculateThroughput(metrics)
                ));
    }

    /**
     * Get comprehensive job statistics by connector type
     *
     * This endpoint provides a high-level overview of job performance across
     * all connector types, essential for system-wide monitoring and reporting.
     *
     * @return Statistics aggregated by connector type
     */
    @GetMapping("/stats/by-connector")
    public Flux<Map<String, Object>> getJobStatsByConnectorType() {
        return jobService.getJobStatsByConnectorType()
                .map(stats -> Map.of(
                        "connectorType", stats.getConnectorType(),
                        "totalJobs", stats.getTotalJobs(),
                        "completedJobs", stats.getCompletedJobs(),
                        "failedJobs", stats.getFailedJobs(),
                        "runningJobs", stats.getRunningJobs(),
                        "avgItemsProcessed", stats.getAvgItemsProcessed() != null ?
                                Math.round(stats.getAvgItemsProcessed() * 100.0) / 100.0 : 0,
                        "totalItemsProcessed", stats.getTotalItemsProcessed(),
                        "totalItemsFailed", stats.getTotalItemsFailed(),
                        "successRate", calculateConnectorSuccessRate(stats),
                        "healthStatus", getConnectorHealthStatus(stats),
                        "efficiency", calculateEfficiency(stats)
                ));
    }

    /**
     * Get jobs that have been running longer than expected
     *
     * This endpoint helps identify stuck or problematic jobs that may need
     * manual intervention or investigation.
     *
     * @param minutesThreshold Jobs running longer than this are considered long-running (default: 60)
     * @return List of long-running jobs with detailed information
     */
    @GetMapping("/long-running")
    public Flux<Map<String, Object>> getLongRunningJobs(
            @RequestParam(defaultValue = "60") int minutesThreshold) {

        return jobService.getLongRunningJobs(minutesThreshold)
                .map(job -> {
                    long runningMinutes = Duration.between(job.getStartedAt(), LocalDateTime.now()).toMinutes();

                    return Map.of(
                            "id", job.getId(),
                            "connectorConfigId", job.getConnectorConfigId(),
                            "jobType", job.getJobType(),
                            "status", job.getStatus(),
                            "startedAt", job.getStartedAt().toString(),
                            "runningMinutes", runningMinutes,
                            "itemsProcessed", job.getItemsProcessed(),
                            "itemsFailed", job.getItemsFailed(),
                            "processingRate", calculateCurrentProcessingRate(job),
                            "severity", getLongRunningJobSeverity(runningMinutes),
                            "recommendedAction", getLongRunningJobAction(runningMinutes),
                            "resourceImpact", getResourceImpact(runningMinutes)
                    );
                });
    }

    /**
     * Get system-wide job summary
     *
     * This endpoint provides a quick overview of the entire ingestion system's
     * current state, perfect for dashboards and health checks.
     *
     * @return Current system status summary
     */
    @GetMapping("/summary")
    public Mono<ResponseEntity<Map<String, Object>>> getJobSummary() {
        return Mono.zip(
                jobService.getRunningJobs().count(),
                jobService.getPerformanceMetrics(24).collectList(),
                jobService.getJobStatsByConnectorType().collectList()
        ).map(tuple -> {
            long runningJobs = tuple.getT1();
            var performanceMetrics = tuple.getT2();
            var connectorStats = tuple.getT3();

            // Calculate summary statistics
            long totalJobsLast24h = performanceMetrics.stream()
                    .mapToLong(m -> m.getJobCount())
                    .sum();

            long totalFailuresLast24h = performanceMetrics.stream()
                    .mapToLong(m -> m.getFailedJobs())
                    .sum();

            double overallSuccessRate = totalJobsLast24h > 0 ?
                    ((double)(totalJobsLast24h - totalFailuresLast24h) / totalJobsLast24h) * 100 : 100.0;

            Map<String, Object> summary = Map.of(
                    "currentlyRunning", runningJobs,
                    "totalJobsLast24h", totalJobsLast24h,
                    "successRateLast24h", Math.round(overallSuccessRate * 100.0) / 100.0,
                    "activeConnectors", connectorStats.size(),
                    "systemHealth", getSystemHealth(overallSuccessRate, runningJobs),
                    "timestamp", LocalDateTime.now(),
                    "connectorBreakdown", connectorStats.stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    s -> s.getConnectorType(),
                                    s -> Map.of(
                                            "total", s.getTotalJobs(),
                                            "running", s.getRunningJobs(),
                                            "successRate", calculateConnectorSuccessRate(s)
                                    )
                            ))
            );

            return ResponseEntity.ok(summary);
        });
    }

    /**
     * Cleanup old completed jobs (Admin only)
     *
     * This endpoint removes old job records to manage database size and improve
     * performance. Only completed/failed jobs older than the specified threshold are removed.
     *
     * @param daysToKeep Number of days of job history to retain (default: 30)
     * @return Cleanup operation result
     */
    @DeleteMapping("/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Map<String, Object>>> cleanupOldJobs(
            @RequestParam(defaultValue = "30") int daysToKeep) {

        return jobService.cleanupOldJobs(daysToKeep)
                .map(count -> ResponseEntity.ok(Map.of(
                        "operation", "CLEANUP_COMPLETED",
                        "deletedJobs", count,
                        "retentionDays", daysToKeep,
                        "timestamp", LocalDateTime.now(),
                        "message", String.format("Successfully cleaned up %d old job records", count)
                )))
                .onErrorResume(error ->
                        Mono.just(ResponseEntity.badRequest().body(Map.of(
                                "operation", "CLEANUP_FAILED",
                                "error", error.getMessage(),
                                "timestamp", LocalDateTime.now()
                        )))
                );
    }

    /**
     * Get job failure analysis
     *
     * This endpoint provides detailed analysis of job failures to help identify
     * patterns and root causes for system improvements.
     *
     * @param hoursBack Number of hours to analyze (default: 24)
     * @return Failure analysis with patterns and recommendations
     */
    @GetMapping("/analysis/failures")
    public Flux<Map<String, Object>> getFailureAnalysis(
            @RequestParam(defaultValue = "24") int hoursBack) {

        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);

        return jobService.getJobsForConnector(null) // This would need to be implemented properly
                .filter(job -> "FAILED".equals(job.getStatus()))
                .filter(job -> job.getStartedAt() != null && job.getStartedAt().isAfter(since))
                .collectMultimap(job -> job.getErrorMessage() != null ?
                        extractErrorCategory(job.getErrorMessage()) : "Unknown")
                .flatMapMany(errorMap ->
                        Flux.fromIterable(errorMap.entrySet())
                                .map(entry -> Map.of(
                                        "errorCategory", entry.getKey(),
                                        "occurrences", entry.getValue().size(),
                                        "percentage", (double) entry.getValue().size() / errorMap.size() * 100,
                                        "recommendation", getErrorRecommendation(entry.getKey()),
                                        "severity", getErrorSeverity(entry.getKey())
                                ))
                );
    }

    // Helper methods for calculations and analysis

    private String calculateDuration(IngestionJob job) {
        if (job.getStartedAt() == null) return "N/A";

        LocalDateTime endTime = job.getCompletedAt() != null ? job.getCompletedAt() : LocalDateTime.now();
        Duration duration = Duration.between(job.getStartedAt(), endTime);

        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private double calculateSuccessRate(IngestionJob job) {
        int total = job.getItemsProcessed() + job.getItemsFailed();
        return total > 0 ? (double) job.getItemsProcessed() / total * 100 : 100.0;
    }

    private String getStatusColor(String status) {
        return switch (status) {
            case "COMPLETED" -> "green";
            case "FAILED" -> "red";
            case "RUNNING" -> "blue";
            case "PENDING" -> "yellow";
            default -> "gray";
        };
    }

    private double calculateCurrentProcessingRate(IngestionJob job) {
        if (job.getStartedAt() == null) return 0.0;

        long minutesRunning = Duration.between(job.getStartedAt(), LocalDateTime.now()).toMinutes();
        return minutesRunning > 0 ? (double) job.getItemsProcessed() / minutesRunning : 0.0;
    }

    private double calculateHourlySuccessRate(Object metrics) {
        // This would access the metrics object properties
        // Implementation depends on the actual JobPerformanceMetrics interface
        return 0.0; // Placeholder
    }

    private double calculateThroughput(Object metrics) {
        // Calculate items processed per minute for the hour
        return 0.0; // Placeholder
    }

    private double calculateConnectorSuccessRate(Object stats) {
        // Calculate success rate from connector statistics
        return 0.0; // Placeholder
    }

    private String getConnectorHealthStatus(Object stats) {
        // Determine health status based on statistics
        return "HEALTHY"; // Placeholder
    }

    private double calculateEfficiency(Object stats) {
        // Calculate processing efficiency metric
        return 0.0; // Placeholder
    }

    private String getLongRunningJobSeverity(long runningMinutes) {
        if (runningMinutes > 240) return "CRITICAL";
        if (runningMinutes > 120) return "HIGH";
        if (runningMinutes > 60) return "MEDIUM";
        return "LOW";
    }

    private String getLongRunningJobAction(long runningMinutes) {
        if (runningMinutes > 240) return "Consider terminating and investigating";
        if (runningMinutes > 120) return "Monitor closely, may need intervention";
        if (runningMinutes > 60) return "Monitor for progress";
        return "Normal operation";
    }

    private String getResourceImpact(long runningMinutes) {
        if (runningMinutes > 240) return "HIGH";
        if (runningMinutes > 120) return "MEDIUM";
        return "LOW";
    }

    private String getJobRecommendation(IngestionJob job) {
        if ("FAILED".equals(job.getStatus())) {
            return "Review error logs and retry if needed";
        } else if ("COMPLETED".equals(job.getStatus()) && job.getItemsFailed() > 0) {
            return "Investigate failed items for data quality issues";
        } else if ("RUNNING".equals(job.getStatus())) {
            return "Monitor progress and performance";
        }
        return "No specific recommendations";
    }

    private String getSystemHealth(double successRate, long runningJobs) {
        if (successRate >= 95 && runningJobs < 5) return "EXCELLENT";
        if (successRate >= 90 && runningJobs < 10) return "GOOD";
        if (successRate >= 80) return "FAIR";
        return "NEEDS_ATTENTION";
    }

    private String extractErrorCategory(String errorMessage) {
        if (errorMessage.toLowerCase().contains("timeout")) return "TIMEOUT";
        if (errorMessage.toLowerCase().contains("rate limit")) return "RATE_LIMIT";
        if (errorMessage.toLowerCase().contains("authentication")) return "AUTH_ERROR";
        if (errorMessage.toLowerCase().contains("connection")) return "CONNECTION_ERROR";
        return "OTHER";
    }

    private String getErrorRecommendation(String errorCategory) {
        return switch (errorCategory) {
            case "TIMEOUT" -> "Consider increasing timeout values or optimizing queries";
            case "RATE_LIMIT" -> "Reduce polling frequency or implement better rate limiting";
            case "AUTH_ERROR" -> "Check and refresh API credentials";
            case "CONNECTION_ERROR" -> "Verify network connectivity and service availability";
            default -> "Review logs for specific error details";
        };
    }

    private String getErrorSeverity(String errorCategory) {
        return switch (errorCategory) {
            case "AUTH_ERROR" -> "HIGH";
            case "CONNECTION_ERROR" -> "HIGH";
            case "RATE_LIMIT" -> "MEDIUM";
            case "TIMEOUT" -> "MEDIUM";
            default -> "LOW";
        };
    }
}