package com.openrangelabs.donpetre.ingestion.service;

import com.openrangelabs.donpetre.ingestion.entity.ApiCredential;
import com.openrangelabs.donpetre.ingestion.repository.ApiCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    public CredentialService(ApiCredentialRepository repository) {
        this.repository = repository;
        // TODO: Configure proper encryption key from external source
        this.encryptor = Encryptors.text("password", "salt");
    }

    /**
     * Store encrypted credential
     */
    public Mono<ApiCredential> storeCredential(UUID connectorConfigId, String credentialType,
                                               String plainTextValue, LocalDateTime expiresAt) {
        String encryptedValue = encryptor.encrypt(plainTextValue);

        ApiCredential credential = new ApiCredential(connectorConfigId, credentialType, encryptedValue, expiresAt);

        return repository.save(credential)
                .doOnSuccess(saved -> {
                    logger.info("Stored credential for connector {} of type: {}", connectorConfigId, credentialType);
                    if (expiresAt != null) {
                        logger.info("Credential expires at: {}", expiresAt);
                    }
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
     * Get credentials expiring soon
     */
    public Flux<ApiCredential> getCredentialsExpiringSoon(int daysThreshold) {
        LocalDateTime threshold = LocalDateTime.now().plusDays(daysThreshold);
        return repository.findExpiringSoon(threshold)
                .doOnNext(credential -> logger.warn("Credential {} expires at: {}",
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
     * Get credential statistics
     */
    public Flux<ApiCredentialRepository.CredentialStats> getCredentialStats() {
        return repository.getCredentialStats();
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
}