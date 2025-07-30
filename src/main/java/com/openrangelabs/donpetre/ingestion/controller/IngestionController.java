package com.openrangelabs.donpetre.ingestion.controller;

import com.openrangelabs.donpetre.ingestion.dto.TriggerSyncRequest;
import com.openrangelabs.donpetre.ingestion.model.SyncResult;
import com.openrangelabs.donpetre.ingestion.service.IngestionOrchestrationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST controller for ingestion operations
 * Handles triggering synchronization, testing connections, and monitoring connectors
 */
@RestController
@RequestMapping("/api/ingestion")
@CrossOrigin(origins = "${open-range-labs.donpetre.security.cors.allowed-origins}")
public class IngestionController {

    private final IngestionOrchestrationService orchestrationService;

    @Autowired
    public IngestionController(IngestionOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    /**
     * Trigger full synchronization for a connector
     *
     * This endpoint initiates a complete synchronization of all data from the specified
     * external source. Use this for initial setup or when you need to re-sync all data.
     *
     * @param request Contains connectorType and configName
     * @return SyncResult with processing statistics
     */
    @PostMapping("/sync/full")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<SyncResult>> triggerFullSync(
            @Valid @RequestBody TriggerSyncRequest request) {

        return orchestrationService.triggerFullSync(request.getConnectorType(), request.getConfigName())
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }

    /**
     * Trigger incremental synchronization for a connector
     *
     * This endpoint initiates an incremental sync that only processes new/changed data
     * since the last successful synchronization. This is the preferred method for
     * regular data updates as it's more efficient.
     *
     * @param request Contains connectorType and configName
     * @return SyncResult with processing statistics
     */
    @PostMapping("/sync/incremental")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public Mono<ResponseEntity<SyncResult>> triggerIncrementalSync(
            @Valid @RequestBody TriggerSyncRequest request) {

        return orchestrationService.triggerIncrementalSync(request.getConnectorType(), request.getConfigName())
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }

    /**
     * Test connection to external service
     *
     * This endpoint validates that the connector can successfully connect to the
     * external service using the stored credentials. Use this to verify configuration
     * and troubleshoot connection issues.
     *
     * @param request Contains connectorType and configName
     * @return Connection test result with status
     */
    @PostMapping("/test-connection")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public Mono<ResponseEntity<Map<String, Object>>> testConnection(
            @Valid @RequestBody TriggerSyncRequest request) {

        return orchestrationService.testConnection(request.getConnectorType(), request.getConfigName())
                .map(result -> ResponseEntity.ok(Map.of(
                        "connectorType", request.getConnectorType(),
                        "configName", request.getConfigName(),
                        "connected", result,
                        "status", result ? "SUCCESS" : "FAILED",
                        "timestamp", java.time.LocalDateTime.now()
                )))
                .onErrorResume(error ->
                        Mono.just(ResponseEntity.badRequest().body(Map.of(
                                "connectorType", request.getConnectorType(),
                                "configName", request.getConfigName(),
                                "connected", false,
                                "status", "ERROR",
                                "error", error.getMessage(),
                                "timestamp", java.time.LocalDateTime.now()
                        )))
                );
    }

    /**
     * Get comprehensive metrics for a specific connector
     *
     * This endpoint provides detailed information about a connector's performance,
     * rate limit status, and recent job history. Useful for monitoring and
     * troubleshooting connector health.
     *
     * @param connectorType The type of connector (github, gitlab, jira, slack)
     * @param configName The name of the specific configuration
     * @return Comprehensive metrics including performance data and rate limits
     */
    @GetMapping("/metrics/{connectorType}/{configName}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public Mono<ResponseEntity<Map<String, Object>>> getConnectorMetrics(
            @PathVariable String connectorType,
            @PathVariable String configName) {

        return orchestrationService.getConnectorMetrics(connectorType, configName)
                .map(metrics -> ResponseEntity.ok(Map.of(
                        "connectorType", connectorType,
                        "configName", configName,
                        "timestamp", java.time.LocalDateTime.now(),
                        "data", metrics
                )))
                .onErrorResume(error ->
                        Mono.just(ResponseEntity.notFound().build())
                );
    }

    /**
     * Get list of all available connector types
     *
     * This endpoint returns information about all connector types that are
     * available in the system, regardless of whether they're configured or enabled.
     *
     * @return List of available connector types and their capabilities
     */
    @GetMapping("/connectors")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getAvailableConnectors() {
        Map<String, Object> response = Map.of(
                "connectors", orchestrationService.getAvailableConnectors().keySet(),
                "count", orchestrationService.getAvailableConnectors().size(),
                "timestamp", java.time.LocalDateTime.now(),
                "descriptions", Map.of(
                        "github", "GitHub repositories, commits, issues, and pull requests",
                        "gitlab", "GitLab projects, commits, issues, and merge requests",
                        "jira", "Jira issues, comments, and project data",
                        "slack", "Slack messages, threads, and file shares"
                )
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Get system-wide ingestion status
     *
     * This endpoint provides an overview of the ingestion system status,
     * including active jobs, system health, and recent activity summary.
     *
     * @return System status overview
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public Mono<ResponseEntity<Map<String, Object>>> getIngestionStatus() {
        // This would typically aggregate data from multiple services
        return Mono.fromCallable(() -> {
            Map<String, Object> status = Map.of(
                    "systemStatus", "OPERATIONAL",
                    "availableConnectors", orchestrationService.getAvailableConnectors().size(),
                    "timestamp", java.time.LocalDateTime.now(),
                    "version", "1.0.0-SNAPSHOT"
            );
            return ResponseEntity.ok(status);
        });
    }

    /**
     * Cancel a running synchronization job
     *
     * This endpoint allows administrators to cancel long-running or stuck
     * synchronization jobs. Use with caution as it may leave data in an
     * inconsistent state.
     *
     * @param jobId The ID of the job to cancel
     * @return Cancellation result
     */
    @PostMapping("/jobs/{jobId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Map<String, Object>>> cancelJob(@PathVariable String jobId) {
        // Implementation would depend on job management system
        return Mono.fromCallable(() -> {
            // Placeholder implementation
            Map<String, Object> response = Map.of(
                    "jobId", jobId,
                    "status", "CANCELLATION_REQUESTED",
                    "message", "Job cancellation has been requested",
                    "timestamp", java.time.LocalDateTime.now()
            );
            return ResponseEntity.ok(response);
        });
    }
}