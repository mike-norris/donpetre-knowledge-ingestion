package com.openrangelabs.donpetre.ingestion.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.openrangelabs.donpetre.ingestion.dto.CreateConnectorConfigRequest;
import com.openrangelabs.donpetre.ingestion.dto.UpdateConnectorConfigRequest;
import com.openrangelabs.donpetre.ingestion.entity.ConnectorConfig;
import com.openrangelabs.donpetre.ingestion.service.ConnectorConfigService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for connector configuration management
 */
@RestController
@RequestMapping("/api/connectors")
@CrossOrigin(origins = "${open-range-labs.donpetre.security.cors.allowed-origins}")
public class ConnectorConfigController {

    private final ConnectorConfigService configService;

    @Autowired
    public ConnectorConfigController(ConnectorConfigService configService) {
        this.configService = configService;
    }

    /**
     * Create a new connector configuration
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ConnectorConfig>> createConfiguration(
            @Valid @RequestBody CreateConnectorConfigRequest request) {

        // TODO: Extract user ID from security context
        UUID createdBy = UUID.randomUUID(); // Placeholder

        return configService.createConfiguration(
                        request.getConnectorType(),
                        request.getName(),
                        request.getConfiguration(),
                        createdBy
                )
                .map(config -> ResponseEntity.status(HttpStatus.CREATED).body(config))
                .onErrorReturn(ResponseEntity.badRequest().build());
    }

    /**
     * Get all connector configurations
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public Flux<ConnectorConfig> getAllConfigurations() {
        return configService.getConfigurationsByType(null);
    }

    /**
     * Get configurations by type
     */
    @GetMapping("/type/{connectorType}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public Flux<ConnectorConfig> getConfigurationsByType(@PathVariable String connectorType) {
        return configService.getConfigurationsByType(connectorType);
    }

    /**
     * Get enabled configurations
     */
    @GetMapping("/enabled")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public Flux<ConnectorConfig> getEnabledConfigurations() {
        return configService.getEnabledConfigurations();
    }

    /**
     * Get specific configuration
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public Mono<ResponseEntity<ConnectorConfig>> getConfiguration(@PathVariable UUID id) {
        return configService.getConfiguration(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Update connector configuration
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ConnectorConfig>> updateConfiguration(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConnectorConfigRequest request) {

        return configService.updateConfiguration(id, request.getConfiguration())
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }

    /**
     * Enable or disable connector
     */
    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ConnectorConfig>> setEnabled(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> request) {

        boolean enabled = request.getOrDefault("enabled", false);
        return configService.setEnabled(id, enabled)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }

    /**
     * Delete connector configuration
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> deleteConfiguration(@PathVariable UUID id) {
        return configService.deleteConfiguration(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorReturn(ResponseEntity.badRequest().build());
    }

    /**
     * Get connector type statistics
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<Map<String, Object>> getConnectorStats() {
        return configService.getConnectorTypeStats()
                .map(stats -> Map.of(
                        "connectorType", stats.getConnectorType(),
                        "totalCount", stats.getTotalCount(),
                        "enabledCount", stats.getEnabledCount()
                ));
    }
}