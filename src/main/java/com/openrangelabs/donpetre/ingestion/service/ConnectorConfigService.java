package com.openrangelabs.donpetre.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.openrangelabs.donpetre.ingestion.entity.ConnectorConfig;
import com.openrangelabs.donpetre.ingestion.repository.ConnectorConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing connector configurations
 */
@Service
public class ConnectorConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorConfigService.class);

    private final ConnectorConfigRepository repository;
    private final CredentialService credentialService;

    @Autowired
    public ConnectorConfigService(ConnectorConfigRepository repository, CredentialService credentialService) {
        this.repository = repository;
        this.credentialService = credentialService;
    }

    /**
     * Create a new connector configuration
     */
    public Mono<ConnectorConfig> createConfiguration(String connectorType, String name,
                                                     JsonNode configuration, UUID createdBy) {
        return repository.existsByConnectorTypeAndName(connectorType, name)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException(
                                "Configuration with type '" + connectorType + "' and name '" + name + "' already exists"));
                    }

                    ConnectorConfig config = new ConnectorConfig(connectorType, name, configuration);
                    config.setCreatedBy(createdBy);
                    config.setEnabled(false); // Default to disabled

                    return repository.save(config)
                            .doOnSuccess(saved -> logger.info("Created connector configuration: {} - {}",
                                    connectorType, name));
                });
    }

    /**
     * Update connector configuration
     */
    public Mono<ConnectorConfig> updateConfiguration(UUID id, JsonNode configuration) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Configuration not found: " + id)))
                .flatMap(config -> {
                    config.setConfiguration(configuration);
                    config.setUpdatedAt(LocalDateTime.now());
                    return repository.save(config)
                            .doOnSuccess(updated -> logger.info("Updated connector configuration: {}", id));
                });
    }

    /**
     * Enable or disable connector
     */
    public Mono<ConnectorConfig> setEnabled(UUID id, boolean enabled) {
        return repository.updateEnabledStatus(id, enabled)
                .then(repository.findById(id))
                .doOnSuccess(config -> logger.info("Set connector {} enabled status to: {}", id, enabled));
    }

    /**
     * Get configuration by ID
     */
    public Mono<ConnectorConfig> getConfiguration(UUID id) {
        return repository.findById(id);
    }

    /**
     * Get configuration by type and name
     */
    public Mono<ConnectorConfig> getConfiguration(String connectorType, String name) {
        return repository.findByConnectorTypeAndName(connectorType, name);
    }

    /**
     * Get all enabled configurations
     */
    public Flux<ConnectorConfig> getEnabledConfigurations() {
        return repository.findAllEnabled();
    }

    /**
     * Get enabled configurations by type
     */
    public Flux<ConnectorConfig> getEnabledConfigurations(String connectorType) {
        return repository.findEnabledByConnectorType(connectorType);
    }

    /**
     * Get all configurations by type
     */
    public Flux<ConnectorConfig> getConfigurationsByType(String connectorType) {
        return repository.findByConnectorType(connectorType);
    }

    /**
     * Delete configuration
     */
    public Mono<Void> deleteConfiguration(UUID id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Configuration not found: " + id)))
                .flatMap(config ->
                        // First deactivate all credentials
                        credentialService.deactivateAllCredentials(id)
                                .then(repository.deleteById(id))
                                .doOnSuccess(v -> logger.info("Deleted connector configuration: {} - {}",
                                        config.getConnectorType(), config.getName()))
                );
    }

    /**
     * Get connector type statistics
     */
    public Flux<ConnectorConfigRepository.ConnectorTypeStats> getConnectorTypeStats() {
        return repository.getConnectorTypeStats();
    }

    /**
     * Validate configuration structure (override in specific implementations)
     */
    public Mono<Boolean> validateConfiguration(ConnectorConfig config) {
        // Basic validation - check if configuration is not null
        if (config.getConfiguration() == null) {
            return Mono.just(false);
        }

        // Override this method in connector-specific services for detailed validation
        return Mono.just(true);
    }

    /**
     * Get configurations created by user
     */
    public Flux<ConnectorConfig> getConfigurationsByCreator(UUID createdBy) {
        return repository.findByCreatedBy(createdBy);
    }

    /**
     * Get configurations updated since a specific time
     */
    public Flux<ConnectorConfig> getConfigurationsUpdatedSince(LocalDateTime since) {
        return repository.findUpdatedSince(since);
    }
}