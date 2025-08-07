package com.openrangelabs.donpetre.ingestion.service;

import com.openrangelabs.donpetre.ingestion.dto.CredentialResponseDto;
import com.openrangelabs.donpetre.ingestion.dto.CredentialStatsDto;
import com.openrangelabs.donpetre.ingestion.dto.CredentialUsageDto;
import com.openrangelabs.donpetre.ingestion.dto.StoreCredentialRequest;
import com.openrangelabs.donpetre.ingestion.entity.ApiCredential;
import com.openrangelabs.donpetre.ingestion.exception.CredentialAlreadyExistsException;
import com.openrangelabs.donpetre.ingestion.exception.CredentialNotFoundException;
import com.openrangelabs.donpetre.ingestion.repository.ApiCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing encrypted API credentials
 */
@Service
public class CredentialService {

    private static final Logger logger = LoggerFactory.getLogger(CredentialService.class);

    private final ApiCredentialRepository repository;
    private final TextEncryptor encryptor;

    @Autowired
    public CredentialService(ApiCredentialRepository repository,
                           @Value("${open-range-labs.donpetre.encryption.secret-key}") String encryptionKey) {
        this.repository = repository;
        // Use no-op encryption for development (TODO: implement proper encryption)
        // This allows the service to start while we work on proper encryption implementation
        this.encryptor = Encryptors.noOpText();
    }

    /**
     * Store encrypted credential with description support
     * This overloads your existing storeCredential method to support description
     */
    public Mono<ApiCredential> storeCredential(UUID connectorConfigId, String credentialType,
                                               String plainTextValue, LocalDateTime expiresAt, String description) {
        String encryptedValue = encryptor.encrypt(plainTextValue);
        ApiCredential credential = new ApiCredential(connectorConfigId, credentialType, encryptedValue, expiresAt);
        credential.setDescription(description); // Set the description if your entity supports it
        return repository.save(credential)
                .doOnSuccess(saved -> logger.info("Stored credential {} for connector {} with description",
                        saved.getId(), connectorConfigId));
    }

    /**
     * Store encrypted credential with description support
     * This overloads your existing storeCredential method to support description
     */
    public Mono<ApiCredential> storeCredential(UUID connectorConfigId, String credentialType,
                                               String plainTextValue, LocalDateTime expiresAt) {
        String encryptedValue = encryptor.encrypt(plainTextValue);
        ApiCredential credential = new ApiCredential(connectorConfigId, credentialType, encryptedValue, expiresAt);
        return repository.save(credential)
                .doOnSuccess(saved -> logger.info("Stored credential {} for connector {} with description",
                        saved.getId(), connectorConfigId));
    }

    /**
     * Get credential usage analytics for a connector
     * This provides detailed usage patterns and statistics
     */
    public Flux<CredentialUsageDto> getCredentialUsageAnalytics(UUID connectorConfigId) {
        return repository.findByConnectorConfigId(connectorConfigId)
                .map(credential -> {
                    // Calculate usage metrics
                    LocalDateTime now = LocalDateTime.now();
                    long daysSinceCreation = Duration.between(credential.getCreatedAt(), now).toDays();

                    // For now, we'll use a simple usage count calculation
                    // In a real implementation, you'd have a separate usage tracking table
                    long estimatedUsageCount = credential.getLastUsed() != null ?
                            Math.max(1, daysSinceCreation / 7) : 0; // Rough estimate

                    return CredentialUsageDto.create(
                            credential.getId(),
                            credential.getConnectorConfigId(),
                            credential.getCredentialType(),
                            credential.getCreatedAt(),
                            credential.getLastUsed(),
                            estimatedUsageCount
                    );
                });
    }

    /**
     * Retrieve and decrypt credential
     */
    public Mono<String> getDecryptedCredential(UUID connectorConfigId, String credentialType) {
        return repository.findActiveByConnectorConfigIdAndCredentialType(connectorConfigId, credentialType)
                .filter(credential -> !credential.isExpired())
                .map(credential -> {
                    credential.markAsUsed();
                    repository.updateLastUsed(credential.getId()).subscribe();
                    return encryptor.decrypt(credential.getEncryptedValue());
                })
                .doOnNext(value -> logger.debug("Retrieved credential for connector {} of type: {}",
                        connectorConfigId, credentialType))
                .doOnError(error -> logger.error("Failed to retrieve credential for connector {} of type: {}",
                        connectorConfigId, credentialType, error));
    }

    /**
     * Helper method to update credential usage timestamp
     * Call this whenever a credential is used
     */
    public Mono<Void> recordCredentialUsage(UUID credentialId) {
        return repository.findById(credentialId)
                .flatMap(credential -> {
                    credential.setLastUsed(LocalDateTime.now());
                    return repository.save(credential);
                })
                .then()
                .doOnSuccess(v -> logger.debug("Recorded usage for credential {}", credentialId));
    }

    /**
     * Update credential
     */
    public Mono<ApiCredential> updateCredential(UUID credentialId, String plainTextValue, LocalDateTime expiresAt) {
        return repository.findById(credentialId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Credential not found: " + credentialId)))
                .flatMap(credential -> {
                    credential.setEncryptedValue(encryptor.encrypt(plainTextValue));
                    credential.setExpiresAt(expiresAt);
                    return repository.save(credential)
                            .doOnSuccess(updated -> logger.info("Updated credential: {}", credentialId));
                });
    }

    /**
     * Deactivate credential
     */
    public Mono<Void> deactivateCredential(UUID credentialId) {
        return repository.deactivate(credentialId)
                .then()
                .doOnSuccess(v -> logger.info("Deactivated credential: {}", credentialId));
    }

    /**
     * Deactivate all credentials for a connector
     */
    public Mono<Void> deactivateAllCredentials(UUID connectorConfigId) {
        return repository.deactivateAllForConnector(connectorConfigId)
                .then()
                .doOnSuccess(v -> logger.info("Deactivated all credentials for connector: {}", connectorConfigId));
    }

    /**
     * Enhanced method to get credentials expiring within specified days
     * This provides more detailed expiration information
     */
    public Flux<ApiCredential> getCredentialsExpiringSoon(int daysThreshold) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thresholdDate = now.plusDays(daysThreshold);

        return repository.findByExpirationRange(now, thresholdDate)
                .filter(credential -> credential.getIsActive())
                .doOnNext(credential -> logger.debug("Found credential {} expiring on {}",
                        credential.getId(), credential.getExpiresAt()));
    }

    /**
     * Get expired credentials
     */
    public Flux<ApiCredential> getExpiredCredentials() {
        return repository.findExpired()
                .doOnNext(credential -> logger.warn("Credential {} expired at: {}",
                        credential.getId(), credential.getExpiresAt()));
    }

    /**
     * Get all credentials for a connector (without decryption)
     */
    public Flux<ApiCredential> getCredentials(UUID connectorConfigId) {
        return repository.findByConnectorConfigId(connectorConfigId);
    }

    /**
     * Get active credentials for a connector (without decryption)
     */
    public Flux<ApiCredential> getActiveCredentials(UUID connectorConfigId) {
        return repository.findActiveByConnectorConfigId(connectorConfigId);
    }

    /**
     * Check if credential exists and is valid
     */
    public Mono<Boolean> hasValidCredential(UUID connectorConfigId, String credentialType) {
        return repository.findActiveByConnectorConfigIdAndCredentialType(connectorConfigId, credentialType)
                .map(credential -> !credential.isExpired())
                .defaultIfEmpty(false);
    }

    /**
     * Enhanced credential statistics that returns CredentialStatsDto
     * This transforms your existing stats into the enhanced DTO format
     */
    public Flux<CredentialStatsDto> getCredentialStats() {
        return repository.getCredentialStats()
                .map(stats -> {
                    // Calculate expiring soon count
                    return repository.findByExpirationRange(
                                    LocalDateTime.now(),
                                    LocalDateTime.now().plusDays(7)
                            )
                            .filter(cred -> cred.getCredentialType().equals(stats.getCredentialType()))
                            .count()
                            .map(expiringSoonCount ->
                                    CredentialStatsDto.create(
                                            stats.getCredentialType(),
                                            stats.getTotalCount(),
                                            stats.getActiveCount(),
                                            stats.getExpiredCount(),
                                            expiringSoonCount
                                    )
                            );
                })
                .flatMap(mono -> mono); // Flatten the nested Mono
    }

    /**
     * Rotate credential (deactivate old, create new)
     */
    public Mono<ApiCredential> rotateCredential(UUID connectorConfigId, String credentialType,
                                                String newPlainTextValue, LocalDateTime expiresAt) {
        return repository.findActiveByConnectorConfigIdAndCredentialType(connectorConfigId, credentialType)
                .flatMap(oldCredential ->
                        deactivateCredential(oldCredential.getId())
                                .then(storeCredential(connectorConfigId, credentialType, newPlainTextValue, expiresAt))
                )
                .doOnSuccess(newCredential -> logger.info("Rotated credential for connector {} of type: {}",
                        connectorConfigId, credentialType));
    }

    /**
     * Store credential from request DTO
     */
    public Mono<CredentialResponseDto> storeCredential(StoreCredentialRequest request) {
        return repository.existsByConnectorIdAndCredentialName(request.getConnectorConfigId(), request.getCredentialType())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new CredentialAlreadyExistsException(
                                request.getConnectorConfigId(), request.getCredentialType()));
                    }

                    String encryptedValue = encryptor.encrypt(request.getValue());
                    ApiCredential credential = new ApiCredential();
                    credential.setConnectorConfigId(request.getConnectorConfigId());
                    credential.setCredentialType(request.getCredentialType());
                    credential.setEncryptedValue(encryptedValue);
                    credential.setExpiresAt(request.getExpiresAt());

                    return repository.save(credential);
                })
                .map(this::toResponseDto)
                .doOnSuccess(response -> logger.info("Stored credential {} for connector {}", 
                        response.getId(), request.getConnectorConfigId()));
    }

    /**
     * Get credential by ID (returns DTO without decrypted value)
     */
    public Mono<CredentialResponseDto> getCredential(UUID credentialId) {
        return repository.findById(credentialId)
                .map(this::toResponseDto);
    }

    /**
     * Get credentials by connector ID
     */
    public Flux<CredentialResponseDto> getCredentialsByConnector(UUID connectorConfigId) {
        return repository.findByConnectorConfigId(connectorConfigId)
                .map(this::toResponseDto);
    }


    /**
     * Update credential value
     */
    public Mono<CredentialResponseDto> updateCredential(UUID credentialId, String newPlainTextValue) {
        return repository.findById(credentialId)
                .switchIfEmpty(Mono.error(new CredentialNotFoundException("Credential not found: " + credentialId)))
                .flatMap(credential -> {
                    credential.setEncryptedValue(encryptor.encrypt(newPlainTextValue));
                    return repository.save(credential);
                })
                .map(this::toResponseDto)
                .doOnSuccess(response -> logger.info("Updated credential: {}", credentialId));
    }

    /**
     * Delete credential by ID
     */
    public Mono<Void> deleteCredential(UUID credentialId) {
        return repository.deleteById(credentialId)
                .doOnSuccess(v -> logger.info("Deleted credential: {}", credentialId));
    }

    /**
     * Delete all credentials for a connector
     */
    public Mono<Long> deleteCredentialsByConnector(UUID connectorId) {
        return repository.deleteByConnectorId(connectorId)
                .doOnSuccess(count -> logger.info("Deleted {} credentials for connector: {}", count, connectorId));
    }

    /**
     * Get credential usage statistics for a connector
     */
    public Flux<CredentialUsageStats> getCredentialUsage(UUID connectorId) {
        return repository.findCredentialUsage(connectorId)
                .map(result -> new CredentialUsageStats(result.getCredentialName(), result.getLastUsed()));
    }

    /**
     * Rotate credential by ID
     */
    public Mono<ApiCredential> rotateCredential(UUID credentialId, String newValue) {
        return repository.findById(credentialId)
                .flatMap(credential -> {
                    credential.setEncryptedValue(encryptor.encrypt(newValue));
                    return repository.save(credential);
                })
                .doOnSuccess(credential -> logger.info("Rotated credential: {}", credentialId));
    }

    /**
     * Test if credential can be decrypted successfully
     */
    public Mono<Boolean> testCredential(UUID connectorId, String credentialName) {
        return repository.findByConnectorIdAndCredentialName(connectorId, credentialName)
                .map(credential -> {
                    try {
                        encryptor.decrypt(credential.getEncryptedValue());
                        return true;
                    } catch (Exception e) {
                        logger.warn("Failed to decrypt credential for testing: {}", e.getMessage());
                        return false;
                    }
                })
                .defaultIfEmpty(false);
    }

    /**
     * Convert ApiCredential entity to CredentialResponseDto
     */
    private CredentialResponseDto toResponseDto(ApiCredential credential) {
        return CredentialResponseDto.fromEntity(credential);
    }

    /**
     * Inner class for credential usage statistics
     */
    public static class CredentialUsageStats {
        private final String credentialName;
        private final LocalDateTime lastUsed;

        public CredentialUsageStats(String credentialName, LocalDateTime lastUsed) {
            this.credentialName = credentialName;
            this.lastUsed = lastUsed;
        }

        public String getCredentialName() { return credentialName; }
        public LocalDateTime getLastUsed() { return lastUsed; }
    }

}