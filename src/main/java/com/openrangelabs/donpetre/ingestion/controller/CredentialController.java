package com.openrangelabs.donpetre.ingestion.controller;

import com.openrangelabs.donpetre.ingestion.dto.StoreCredentialRequest;
import com.openrangelabs.donpetre.ingestion.entity.ApiCredential;
import com.openrangelabs.donpetre.ingestion.service.CredentialService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
 * REST controller for secure credential management
 * Handles encrypted storage, rotation, and monitoring of API credentials
 *
 * Security Note: This controller only handles credential metadata - actual 
 * credential values are never returned in API responses for security reasons.
 */
@RestController
@RequestMapping("/api/credentials")
@CrossOrigin(origins = "${open-range-labs.donpetre.security.cors.allowed-origins}")
@PreAuthorize("hasRole('ADMIN')") // All credential operations require ADMIN role
public class CredentialController {

    private final CredentialService credentialService;

    @Autowired
    public CredentialController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    /**
     * Store new encrypted credential
     *
     * This endpoint securely stores API credentials with AES-256 encryption.
     * The actual credential value is never stored in plain text or logged.
     *
     * @param request Contains connector config ID, credential type, value, and optional expiration
     * @return Success response with credential ID (value is not returned for security)
     */
    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> storeCredential(
            @Valid @RequestBody StoreCredentialRequest request) {

        return credentialService.storeCredential(
                        request.getConnectorConfigId(),
                        request.getCredentialType(),
                        request.getValue(),
                        request.getExpiresAt()
                )
                .map(credential -> ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "id", credential.getId(),
                        "connectorConfigId", credential.getConnectorConfigId(),
                        "credentialType", credential.getCredentialType(),
                        "expiresAt", credential.getExpiresAt() != null ? credential.getExpiresAt().toString() : null,
                        "message", "Credential stored successfully",
                        "encrypted", true,
                        "timestamp", LocalDateTime.now()
                )))
                .onErrorResume(error ->
                        Mono.just(ResponseEntity.badRequest().body(Map.of(
                                "error", "Failed to store credential",
                                "reason", error.getMessage(),
                                "timestamp", LocalDateTime.now()
                        )))
                );
    }

    /**
     * Get credentials for a connector (metadata only - no decrypted values)
     *
     * This endpoint returns credential metadata including expiration status
     * and usage information, but never returns the actual credential values.
     *
     * @param connectorConfigId The connector configuration ID
     * @return List of credential metadata (without actual credential values)
     */
    @GetMapping("/connector/{connectorConfigId}")
    public Flux<Map<String, Object>> getCredentials(@PathVariable UUID connectorConfigId) {
        return credentialService.getActiveCredentials(connectorConfigId)
                .map(credential -> {
                    Map<String, Object> credentialInfo = Map.of(
                            "id", credential.getId(),
                            "credentialType", credential.getCredentialType(),
                            "expiresAt", credential.getExpiresAt() != null ? credential.getExpiresAt().toString() : null,
                            "lastUsed", credential.getLastUsed() != null ? credential.getLastUsed().toString() : null,
                            "isExpired", credential.isExpired(),
                            "isExpiringSoon", credential.isExpiringSoon(7),
                            "createdAt", credential.getCreatedAt().toString(),
                            "isActive", credential.getIsActive()
                    );

                    // Add expiration warning details if applicable
                    if (credential.getExpiresAt() != null) {
                        long daysUntilExpiration = Duration.between(LocalDateTime.now(), credential.getExpiresAt()).toDays();
                        Map<String, Object> mutableMap = new java.util.HashMap<>(credentialInfo);
                        mutableMap.put("daysUntilExpiration", daysUntilExpiration);
                        mutableMap.put("expirationStatus", getExpirationStatus(credential));
                        return mutableMap;
                    }

                    return credentialInfo;
                });
    }

    /**
     * Update existing credential
     *
     * This endpoint allows updating the credential value and/or expiration date.
     * The old credential is securely overwritten with the new encrypted value.
     *
     * @param credentialId The ID of the credential to update
     * @param request New credential value and optional expiration
     * @return Update confirmation
     */
    @PutMapping("/{credentialId}")
    public Mono<ResponseEntity<Map<String, Object>>> updateCredential(
            @PathVariable UUID credentialId,
            @Valid @RequestBody StoreCredentialRequest request) {

        return credentialService.updateCredential(credentialId, request.getValue(), request.getExpiresAt())
                .map(credential -> ResponseEntity.ok(Map.of(
                        "id", credential.getId(),
                        "credentialType", credential.getCredentialType(),
                        "expiresAt", credential.getExpiresAt() != null ? credential.getExpiresAt().toString() : null,
                        "message", "Credential updated successfully",
                        "timestamp", LocalDateTime.now()
                )))
                .onErrorResume(error ->
                        Mono.just(ResponseEntity.badRequest().body(Map.of(
                                "error", "Failed to update credential",
                                "reason", error.getMessage(),
                                "timestamp", LocalDateTime.now()
                        )))
                );
    }

    /**
     * Deactivate credential (soft delete)
     *
     * This endpoint deactivates a credential, making it unusable but preserving
     * the record for audit purposes. The encrypted value remains but is marked inactive.
     *
     * @param credentialId The ID of the credential to deactivate
     * @return Deactivation confirmation
     */
    @DeleteMapping("/{credentialId}")
    public Mono<ResponseEntity<Map<String, Object>>> deactivateCredential(@PathVariable UUID credentialId) {
        return credentialService.deactivateCredential(credentialId)
                .then(Mono.just(ResponseEntity.ok(Map.of(
                        "id", credentialId,
                        "status", "DEACTIVATED",
                        "message", "Credential deactivated successfully",
                        "timestamp", LocalDateTime.now(),
                        "note", "Credential record preserved for audit purposes"
                ))))
                .onErrorResume(error ->
                        Mono.just(ResponseEntity.badRequest().body(Map.of(
                                "error", "Failed to deactivate credential",
                                "reason", error.getMessage(),
                                "timestamp", LocalDateTime.now()
                        )))
                );
    }

    /**
     * Get credentials expiring soon
     *
     * This endpoint returns credentials that are approaching their expiration date.
     * Useful for proactive credential rotation and avoiding service interruptions.
     *
     * @param daysThreshold Number of days ahead to check for expirations (default: 7)
     * @return List of credentials expiring within the threshold
     */
    @GetMapping("/expiring")
    public Flux<Map<String, Object>> getExpiringSoonCredentials(
            @RequestParam(defaultValue = "7") int daysThreshold) {

        return credentialService.getCredentialsExpiringSoon(daysThreshold)
                .map(credential -> Map.of(
                        "id", credential.getId(),
                        "connectorConfigId", credential.getConnectorConfigId(),
                        "credentialType", credential.getCredentialType(),
                        "expiresAt", credential.getExpiresAt().toString(),
                        "daysUntilExpiration", Duration.between(LocalDateTime.now(), credential.getExpiresAt()).toDays(),
                        "expirationStatus", getExpirationStatus(credential),
                        "urgency", getExpirationUrgency(credential),
                        "lastUsed", credential.getLastUsed() != null ? credential.getLastUsed().toString() : "Never"
                ));
    }

    /**
     * Get expired credentials
     *
     * This endpoint returns credentials that have already expired and need
     * immediate attention to restore service functionality.
     *
     * @return List of expired credentials
     */
    @GetMapping("/expired")
    public Flux<Map<String, Object>> getExpiredCredentials() {
        return credentialService.getExpiredCredentials()
                .map(credential -> Map.of(
                        "id", credential.getId(),
                        "connectorConfigId", credential.getConnectorConfigId(),
                        "credentialType", credential.getCredentialType(),
                        "expiredAt", credential.getExpiresAt().toString(),
                        "daysExpired", Duration.between(credential.getExpiresAt(), LocalDateTime.now()).toDays(),
                        "status", "EXPIRED",
                        "lastUsed", credential.getLastUsed() != null ? credential.getLastUsed().toString() : "Never",
                        "actionRequired", "Immediate credential renewal required"
                ));
    }

    /**
     * Get credential statistics
     *
     * This endpoint provides aggregate statistics about credentials across
     * all connector types, useful for operational monitoring and reporting.
     *
     * @return Statistics grouped by credential type
     */
    @GetMapping("/stats")
    public Flux<Map<String, Object>> getCredentialStats() {
        return credentialService.getCredentialStats()
                .map(stats -> Map.of(
                        "credentialType", stats.getCredentialType(),
                        "totalCount", stats.getTotalCount(),
                        "activeCount", stats.getActiveCount(),
                        "expiredCount", stats.getExpiredCount(),
                        "healthPercentage", calculateHealthPercentage(stats.getActiveCount(), stats.getTotalCount()),
                        "status", stats.getExpiredCount() > 0 ? "ATTENTION_REQUIRED" : "HEALTHY"
                ));
    }

    /**
     * Rotate credential (deactivate old, create new)
     *
     * This endpoint performs secure credential rotation by deactivating the old
     * credential and creating a new one atomically. This is the preferred method
     * for credential updates as it maintains audit trails.
     *
     * @param connectorConfigId The connector configuration ID
     * @param request New credential information
     * @return Rotation result with new credential ID
     */
    @PostMapping("/{connectorConfigId}/rotate")
    public Mono<ResponseEntity<Map<String, Object>>> rotateCredential(
            @PathVariable UUID connectorConfigId,
            @Valid @RequestBody StoreCredentialRequest request) {

        return credentialService.rotateCredential(
                        connectorConfigId,
                        request.getCredentialType(),
                        request.getValue(),
                        request.getExpiresAt()
                )
                .map(credential -> ResponseEntity.ok(Map.of(
                        "id", credential.getId(),
                        "connectorConfigId", connectorConfigId,
                        "credentialType", credential.getCredentialType(),
                        "expiresAt", credential.getExpiresAt() != null ? credential.getExpiresAt().toString() : null,
                        "message", "Credential rotated successfully",
                        "operation", "ROTATION",
                        "timestamp", LocalDateTime.now(),
                        "note", "Old credential has been deactivated"
                )))
                .onErrorResume(error ->
                        Mono.just(ResponseEntity.badRequest().body(Map.of(
                                "error", "Failed to rotate credential",
                                "reason", error.getMessage(),
                                "timestamp", LocalDateTime.now()
                        )))
                );
    }

    /**
     * Validate credential without exposing the value
     *
     * This endpoint checks if a credential exists and is valid (not expired)
     * without returning the actual credential value.
     *
     * @param connectorConfigId The connector configuration ID
     * @param credentialType The type of credential to validate
     * @return Validation result
     */
    @GetMapping("/validate/{connectorConfigId}/{credentialType}")
    public Mono<ResponseEntity<Map<String, Object>>> validateCredential(
            @PathVariable UUID connectorConfigId,
            @PathVariable String credentialType) {

        return credentialService.hasValidCredential(connectorConfigId, credentialType)
                .map(isValid -> ResponseEntity.ok(Map.of(
                        "connectorConfigId", connectorConfigId,
                        "credentialType", credentialType,
                        "isValid", isValid,
                        "status", isValid ? "VALID" : "INVALID_OR_EXPIRED",
                        "timestamp", LocalDateTime.now()
                )))
                .onErrorReturn(ResponseEntity.badRequest().body(Map.of(
                        "error", "Validation failed",
                        "timestamp", LocalDateTime.now()
                )));
    }

    /**
     * Get credential usage history
     *
     * This endpoint provides information about when credentials were last used,
     * helping identify unused or frequently accessed credentials.
     *
     * @param connectorConfigId The connector configuration ID
     * @return Usage information for credentials
     */
    @GetMapping("/usage/{connectorConfigId}")
    public Flux<Map<String, Object>> getCredentialUsage(@PathVariable UUID connectorConfigId) {
        return credentialService.getActiveCredentials(connectorConfigId)
                .map(credential -> Map.of(
                        "id", credential.getId(),
                        "credentialType", credential.getCredentialType(),
                        "lastUsed", credential.getLastUsed() != null ? credential.getLastUsed().toString() : "Never",
                        "createdAt", credential.getCreatedAt().toString(),
                        "daysSinceLastUse", credential.getLastUsed() != null ?
                                Duration.between(credential.getLastUsed(), LocalDateTime.now()).toDays() : -1,
                        "usageStatus", getUsageStatus(credential)
                ));
    }

    // Helper methods for status calculation

    private String getExpirationStatus(ApiCredential credential) {
        if (credential.isExpired()) {
            return "EXPIRED";
        } else if (credential.isExpiringSoon(3)) {
            return "CRITICAL";
        } else if (credential.isExpiringSoon(7)) {
            return "WARNING";
        } else if (credential.isExpiringSoon(30)) {
            return "NOTICE";
        } else {
            return "HEALTHY";
        }
    }

    private String getExpirationUrgency(ApiCredential credential) {
        long daysUntilExpiration = Duration.between(LocalDateTime.now(), credential.getExpiresAt()).toDays();
        if (daysUntilExpiration <= 1) return "URGENT";
        if (daysUntilExpiration <= 3) return "HIGH";
        if (daysUntilExpiration <= 7) return "MEDIUM";
        return "LOW";
    }

    private String getUsageStatus(ApiCredential credential) {
        if (credential.getLastUsed() == null) {
            return "UNUSED";
        }

        long daysSinceLastUse = Duration.between(credential.getLastUsed(), LocalDateTime.now()).toDays();
        if (daysSinceLastUse == 0) return "ACTIVE_TODAY";
        if (daysSinceLastUse <= 7) return "RECENT";
        if (daysSinceLastUse <= 30) return "MODERATE";
        return "STALE";
    }

    private double calculateHealthPercentage(Long activeCount, Long totalCount) {
        if (totalCount == 0) return 0.0;
        return (double) activeCount / totalCount * 100.0;
    }
}