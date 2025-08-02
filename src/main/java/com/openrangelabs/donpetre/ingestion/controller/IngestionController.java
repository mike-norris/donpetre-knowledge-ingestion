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

import java.time.LocalDateTime;
import java.util.HashMap;
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
                .map(result -> {
                    // FIXED: Use HashMap instead of Map.of() to avoid generic type conflicts
                    Map<String, Object> response = new HashMap<>();
                    response.put("connectorType", request.getConnectorType());
                    response.put("configName", request.getConfigName());
                    response.put("connected", result);
                    response.put("status", result ? "SUCCESS" : "FAILED");
                    response.put("timestamp", LocalDateTime.now());

                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("connectorType", request.getConnectorType());
                    errorResponse.put("configName", request.getConfigName());
                    errorResponse.put("connected", false);
                    errorResponse.put("status", "ERROR");
                    errorResponse.put("error", error.getMessage());
                    errorResponse.put("timestamp", LocalDateTime.now());

                    return Mono.just(ResponseEntity.badRequest().body(errorResponse));
                });
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
                .map(metrics -> {
                    // FIXED: Use HashMap instead of Map.of() to avoid generic type conflicts (LINE 93 AREA)
                    Map<String, Object> response = new HashMap<>();
                    response.put("connectorType", connectorType);
                    response.put("configName", configName);
                    response.put("timestamp", LocalDateTime.now());
                    response.put("data", metrics);

                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("connectorType", connectorType);
                    errorResponse.put("configName", configName);
                    errorResponse.put("status", "ERROR");
                    errorResponse.put("error", error.getMessage());
                    errorResponse.put("timestamp", LocalDateTime.now());

                    return Mono.just(ResponseEntity.status(404).body(errorResponse));
                });
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
        // FIXED: Use HashMap to avoid generic type conflicts
        Map<String, Object> descriptions = new HashMap<>();
        descriptions.put("github", "GitHub repositories, commits, issues, and pull requests");
        descriptions.put("gitlab", "GitLab projects, commits, issues, and merge requests");
        descriptions.put("jira", "Jira issues, comments, and project data");
        descriptions.put("slack", "Slack messages, threads, and file shares");

        Map<String, Object> response = new HashMap<>();
        response.put("connectors", orchestrationService.getAvailableConnectors().keySet());
        response.put("count", orchestrationService.getAvailableConnectors().size());
        response.put("timestamp", LocalDateTime.now());
        response.put("descriptions", descriptions);

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
            Map<String, Object> status = new HashMap<>();
            status.put("systemStatus", "OPERATIONAL");
            status.put("availableConnectors", orchestrationService.getAvailableConnectors().size());
            status.put("timestamp", LocalDateTime.now());
            status.put("version", "1.0.0-SNAPSHOT");

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
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("status", "CANCELLATION_REQUESTED");
            response.put("message", "Job cancellation has been requested");
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);
        });
    }

    // ADDED: Helper methods for consistent response building

    /**
     * Creates a success response map
     */
    private Map<String, Object> createSuccessResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    /**
     * Creates an error response map
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ERROR");
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    /**
     * Creates an error response map with details
     */
    private Map<String, Object> createErrorResponse(String message, Exception error) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ERROR");
        response.put("message", message);
        response.put("error", error.getMessage());
        response.put("timestamp", LocalDateTime.now());
        return response;
    }
}