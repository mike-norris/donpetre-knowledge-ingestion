package com.openrangelabs.donpetre.ingestion.controller;

import com.openrangelabs.donpetre.ingestion.dto.CredentialResponseDto;
import com.openrangelabs.donpetre.ingestion.dto.CredentialStatsDto;
import com.openrangelabs.donpetre.ingestion.dto.CredentialUsageDto;
import com.openrangelabs.donpetre.ingestion.dto.StoreCredentialRequest;
import com.openrangelabs.donpetre.ingestion.exception.CredentialException;
import com.openrangelabs.donpetre.ingestion.service.CredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * REST controller for secure credential management operations.
 *
 * <p>This controller provides comprehensive credential management functionality including:
 * <ul>
 *   <li>Secure storage with AES-256 encryption</li>
 *   <li>Credential rotation and lifecycle management</li>
 *   <li>Expiration monitoring and alerting</li>
 *   <li>Usage tracking and analytics</li>
 *   <li>Audit trail maintenance</li>
 * </ul>
 *
 * <p><strong>Security Note:</strong> All endpoints require ADMIN role. Credential values
 * are never returned in responses for security reasons. All operations are logged for
 * audit purposes.
 *
 * @author OpenRange Labs
 * @version 1.0
 * @since 2025-01
 */
@Slf4j
@Validated
@RestController
@RequestMapping(value = "/api/credentials", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Credential Management", description = "Secure credential storage and management operations")
@SecurityRequirement(name = "bearerAuth")
public class CredentialController {

    private final CredentialService credentialService;

    /**
     * Stores a new encrypted credential securely.
     *
     * <p>The credential value is immediately encrypted using AES-256 and the plaintext
     * value is never persisted. The operation is idempotent - storing the same credential
     * multiple times will update the existing record.
     *
     * @param request the credential storage request containing value and metadata
     * @return credential metadata response (excluding the actual credential value)
     */
    @Operation(
            summary = "Store new encrypted credential",
            description = "Securely stores API credentials with AES-256 encryption. Credential values are never returned."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Credential stored successfully",
                    content = @Content(schema = @Schema(implementation = CredentialResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required"),
            @ApiResponse(responseCode = "409", description = "Credential already exists for this connector and type"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<CredentialResponseDto>> storeCredential(
            @Valid @RequestBody StoreCredentialRequest request) {

        log.info("Storing credential for connector {} with type {}",
                request.getConnectorConfigId(), request.getCredentialType());

        return credentialService.storeCredential(
                        request.getConnectorConfigId(),
                        request.getCredentialType(),
                        request.getValue(),
                        request.getExpiresAt(),
                        request.getDescription()
                )
                .map(credential -> {
                    CredentialResponseDto response = CredentialResponseDto.fromEntity(credential);
                    log.info("Successfully stored credential {} for connector {}",
                            credential.getId(), request.getConnectorConfigId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .doOnError(error -> log.error("Failed to store credential for connector {}: {}",
                        request.getConnectorConfigId(), error.getMessage()));
    }

    /**
     * Retrieves credential metadata for a specific connector.
     *
     * <p>Returns only metadata about credentials (IDs, types, expiration status, etc.)
     * but never the actual credential values for security reasons.
     *
     * @param connectorConfigId the UUID of the connector configuration
     * @return flux of credential metadata objects
     */
    @Operation(
            summary = "Get credential metadata for connector",
            description = "Returns credential metadata without actual values for security"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credentials retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required"),
            @ApiResponse(responseCode = "404", description = "Connector not found")
    })
    @GetMapping("/connector/{connectorConfigId}")
    public Flux<CredentialResponseDto> getCredentialsForConnector(
            @Parameter(description = "Connector configuration UUID", required = true)
            @PathVariable UUID connectorConfigId) {

        log.debug("Retrieving credentials for connector {}", connectorConfigId);

        return credentialService.getActiveCredentials(connectorConfigId)
                .map(CredentialResponseDto::fromEntity)
                .doOnNext(dto -> log.debug("Found credential {} for connector {}",
                        dto.getId(), connectorConfigId))
                .doOnError(error -> log.error("Failed to retrieve credentials for connector {}: {}",
                        connectorConfigId, error.getMessage()));
    }

    /**
     * Updates an existing credential with new value and/or expiration.
     *
     * <p>The operation securely overwrites the old encrypted value with the new one.
     * All updates are logged for audit purposes.
     *
     * @param credentialId the UUID of the credential to update
     * @param request the update request with new credential data
     * @return updated credential metadata
     */
    @Operation(
            summary = "Update existing credential",
            description = "Updates credential value and/or expiration date with secure encryption"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credential updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required"),
            @ApiResponse(responseCode = "404", description = "Credential not found")
    })
    @PutMapping("/{credentialId}")
    public Mono<ResponseEntity<CredentialResponseDto>> updateCredential(
            @Parameter(description = "Credential UUID", required = true)
            @PathVariable UUID credentialId,
            @Valid @RequestBody StoreCredentialRequest request) {

        log.info("Updating credential {}", credentialId);

        return credentialService.updateCredential(credentialId, request.getValue(), request.getExpiresAt())
                .map(credential -> {
                    CredentialResponseDto response = CredentialResponseDto.fromEntity(credential);
                    log.info("Successfully updated credential {}", credentialId);
                    return ResponseEntity.ok(response);
                })
                .doOnError(error -> log.error("Failed to update credential {}: {}",
                        credentialId, error.getMessage()));
    }

    /**
     * Performs atomic credential rotation by deactivating old and creating new.
     *
     * <p>This is the preferred method for credential updates as it maintains complete
     * audit trails and ensures zero-downtime transitions.
     *
     * @param connectorConfigId the connector configuration UUID
     * @param request the new credential information
     * @return the newly created credential metadata
     */
    @Operation(
            summary = "Rotate credential atomically",
            description = "Deactivates old credential and creates new one in single atomic operation"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credential rotated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required"),
            @ApiResponse(responseCode = "404", description = "Connector not found")
    })
    @PostMapping("/{connectorConfigId}/rotate")
    public Mono<ResponseEntity<CredentialResponseDto>> rotateCredential(
            @Parameter(description = "Connector configuration UUID", required = true)
            @PathVariable UUID connectorConfigId,
            @Valid @RequestBody StoreCredentialRequest request) {

        log.info("Rotating credential for connector {} with type {}",
                connectorConfigId, request.getCredentialType());

        return credentialService.rotateCredential(
                        connectorConfigId,
                        request.getCredentialType(),
                        request.getValue(),
                        request.getExpiresAt()
                )
                .map(credential -> {
                    CredentialResponseDto response = CredentialResponseDto.fromEntity(credential);
                    log.info("Successfully rotated credential for connector {} - new ID: {}",
                            connectorConfigId, credential.getId());
                    return ResponseEntity.ok(response);
                })
                .doOnError(error -> log.error("Failed to rotate credential for connector {}: {}",
                        connectorConfigId, error.getMessage()));
    }

    /**
     * Deactivates a credential (soft delete for audit preservation).
     *
     * <p>The credential is marked as inactive but retained for audit purposes.
     * The encrypted value remains in storage but cannot be retrieved or used.
     *
     * @param credentialId the UUID of the credential to deactivate
     * @return confirmation of deactivation
     */
    @Operation(
            summary = "Deactivate credential",
            description = "Soft delete credential while preserving audit trail"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Credential deactivated successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required"),
            @ApiResponse(responseCode = "404", description = "Credential not found")
    })
    @DeleteMapping("/{credentialId}")
    public Mono<ResponseEntity<Void>> deactivateCredential(
            @Parameter(description = "Credential UUID", required = true)
            @PathVariable UUID credentialId) {

        log.info("Deactivating credential {}", credentialId);

        return credentialService.deactivateCredential(credentialId)
                .then(Mono.fromCallable(() -> {
                    log.info("Successfully deactivated credential {}", credentialId);
                    return ResponseEntity.noContent().<Void>build();
                }))
                .doOnError(error -> log.error("Failed to deactivate credential {}: {}",
                        credentialId, error.getMessage()));
    }

    /**
     * Validates credential existence and expiration status.
     *
     * <p>Performs validation without exposing actual credential values.
     * Useful for health checks and pre-flight validations.
     *
     * @param connectorConfigId the connector configuration UUID
     * @param credentialType the type of credential to validate
     * @return validation result with status information
     */
    @Operation(
            summary = "Validate credential without exposing value",
            description = "Checks credential existence and validity for health monitoring"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Validation completed"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    @GetMapping("/validate/{connectorConfigId}/{credentialType}")
    public Mono<ResponseEntity<CredentialValidationResponse>> validateCredential(
            @Parameter(description = "Connector configuration UUID", required = true)
            @PathVariable UUID connectorConfigId,
            @Parameter(description = "Credential type to validate", required = true)
            @PathVariable String credentialType) {

        log.debug("Validating credential for connector {} with type {}",
                connectorConfigId, credentialType);

        return credentialService.hasValidCredential(connectorConfigId, credentialType)
                .map(isValid -> {
                    CredentialValidationResponse response = CredentialValidationResponse.builder()
                            .connectorConfigId(connectorConfigId)
                            .credentialType(credentialType)
                            .isValid(isValid)
                            .validatedAt(LocalDateTime.now())
                            .build();

                    log.debug("Credential validation for connector {} type {}: {}",
                            connectorConfigId, credentialType, isValid ? "VALID" : "INVALID");

                    return ResponseEntity.ok(response);
                })
                .doOnError(error -> log.error("Failed to validate credential for connector {} type {}: {}",
                        connectorConfigId, credentialType, error.getMessage()));
    }

    /**
     * Retrieves credentials expiring within specified threshold.
     *
     * <p>Proactive monitoring endpoint for credential rotation planning.
     * Helps prevent service interruptions due to expired credentials.
     *
     * @param daysThreshold number of days ahead to check (1-90, default: 7)
     * @return flux of credentials approaching expiration
     */
    @Operation(
            summary = "Get credentials expiring soon",
            description = "Returns credentials approaching expiration for proactive rotation"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Expiring credentials retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid threshold parameter"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    @GetMapping("/expiring")
    public Flux<CredentialExpirationDto> getCredentialsExpiringSoon(
            @Parameter(description = "Days ahead to check for expiration (1-90)")
            @RequestParam(defaultValue = "7")
            @Min(value = 1, message = "Days threshold must be at least 1")
            @Max(value = 90, message = "Days threshold cannot exceed 90")
            int daysThreshold) {

        log.debug("Retrieving credentials expiring within {} days", daysThreshold);

        return credentialService.getCredentialsExpiringSoon(daysThreshold)
                .map(credential -> {
                    Duration timeUntilExpiration = Duration.between(LocalDateTime.now(), credential.getExpiresAt());
                    return CredentialExpirationDto.builder()
                            .id(credential.getId())
                            .connectorConfigId(credential.getConnectorConfigId())
                            .credentialType(credential.getCredentialType())
                            .expiresAt(credential.getExpiresAt())
                            .daysUntilExpiration(timeUntilExpiration.toDays())
                            .urgencyLevel(calculateUrgencyLevel(timeUntilExpiration))
                            .lastUsed(credential.getLastUsed())
                            .build();
                })
                .doOnNext(dto -> log.debug("Found credential {} expiring in {} days",
                        dto.getId(), dto.getDaysUntilExpiration()))
                .doOnError(error -> log.error("Failed to retrieve expiring credentials: {}",
                        error.getMessage()));
    }

    /**
     * Retrieves already expired credentials requiring immediate attention.
     *
     * <p>Critical monitoring endpoint for identifying service disruptions.
     * These credentials need immediate rotation to restore functionality.
     *
     * @return flux of expired credentials
     */
    @Operation(
            summary = "Get expired credentials",
            description = "Returns credentials that have already expired and need immediate attention"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Expired credentials retrieved"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    @GetMapping("/expired")
    public Flux<CredentialExpirationDto> getExpiredCredentials() {

        log.debug("Retrieving expired credentials");

        return credentialService.getExpiredCredentials()
                .map(credential -> {
                    Duration timeSinceExpiration = Duration.between(credential.getExpiresAt(), LocalDateTime.now());
                    return CredentialExpirationDto.builder()
                            .id(credential.getId())
                            .connectorConfigId(credential.getConnectorConfigId())
                            .credentialType(credential.getCredentialType())
                            .expiresAt(credential.getExpiresAt())
                            .daysUntilExpiration(-timeSinceExpiration.toDays()) // Negative for expired
                            .urgencyLevel(UrgencyLevel.CRITICAL)
                            .lastUsed(credential.getLastUsed())
                            .build();
                })
                .doOnNext(dto -> log.warn("Found expired credential {} - expired {} days ago",
                        dto.getId(), Math.abs(dto.getDaysUntilExpiration())))
                .doOnError(error -> log.error("Failed to retrieve expired credentials: {}",
                        error.getMessage()));
    }

    /**
     * Provides aggregate statistics about credential health and status.
     *
     * <p>Operational dashboard endpoint for monitoring credential landscape
     * across all connector types and identifying health trends.
     *
     * @return flux of credential statistics grouped by type
     */
    @Operation(
            summary = "Get credential statistics",
            description = "Returns aggregate statistics for operational monitoring and reporting"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    @GetMapping("/stats")
    public Flux<CredentialStatsDto> getCredentialStatistics() {

        log.debug("Retrieving credential statistics");

        return credentialService.getCredentialStats()
                .doOnNext(stats -> log.debug("Retrieved stats for credential type {}: {} total, {} active",
                        stats.getCredentialType(), stats.getTotalCount(), stats.getActiveCount()))
                .doOnError(error -> log.error("Failed to retrieve credential statistics: {}",
                        error.getMessage()));
    }

    /**
     * Provides detailed usage analytics for credentials under a connector.
     *
     * <p>Analytics endpoint for understanding credential usage patterns,
     * identifying unused credentials, and optimizing rotation schedules.
     *
     * @param connectorConfigId the connector configuration UUID
     * @return flux of credential usage information
     */
    @Operation(
            summary = "Get credential usage analytics",
            description = "Returns detailed usage patterns for credential optimization"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usage analytics retrieved"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required"),
            @ApiResponse(responseCode = "404", description = "Connector not found")
    })
    @GetMapping("/usage/{connectorConfigId}")
    public Flux<CredentialUsageDto> getCredentialUsage(
            @Parameter(description = "Connector configuration UUID", required = true)
            @PathVariable UUID connectorConfigId) {

        log.debug("Retrieving credential usage for connector {}", connectorConfigId);

        return credentialService.getCredentialUsageAnalytics(connectorConfigId)
                .doOnNext(usage -> log.debug("Retrieved usage analytics for credential {} under connector {}",
                        usage.getCredentialId(), connectorConfigId))
                .doOnError(error -> log.error("Failed to retrieve credential usage for connector {}: {}",
                        connectorConfigId, error.getMessage()));
    }

    // Helper methods for business logic

    private UrgencyLevel calculateUrgencyLevel(Duration timeUntilExpiration) {
        long days = timeUntilExpiration.toDays();
        if (days <= 1) return UrgencyLevel.CRITICAL;
        if (days <= 3) return UrgencyLevel.HIGH;
        if (days <= 7) return UrgencyLevel.MEDIUM;
        return UrgencyLevel.LOW;
    }

    // Inner classes for response DTOs

    @Schema(description = "Credential validation response")
    public record CredentialValidationResponse(
            @Schema(description = "Connector configuration ID") UUID connectorConfigId,
            @Schema(description = "Credential type") String credentialType,
            @Schema(description = "Whether credential is valid and not expired") boolean isValid,
            @Schema(description = "Validation timestamp") LocalDateTime validatedAt
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private UUID connectorConfigId;
            private String credentialType;
            private boolean isValid;
            private LocalDateTime validatedAt;

            public Builder connectorConfigId(UUID connectorConfigId) {
                this.connectorConfigId = connectorConfigId;
                return this;
            }

            public Builder credentialType(String credentialType) {
                this.credentialType = credentialType;
                return this;
            }

            public Builder isValid(boolean isValid) {
                this.isValid = isValid;
                return this;
            }

            public Builder validatedAt(LocalDateTime validatedAt) {
                this.validatedAt = validatedAt;
                return this;
            }

            public CredentialValidationResponse build() {
                return new CredentialValidationResponse(connectorConfigId, credentialType, isValid, validatedAt);
            }
        }
    }

    @Schema(description = "Credential expiration information")
    public record CredentialExpirationDto(
            @Schema(description = "Credential ID") UUID id,
            @Schema(description = "Connector configuration ID") UUID connectorConfigId,
            @Schema(description = "Credential type") String credentialType,
            @Schema(description = "Expiration date") LocalDateTime expiresAt,
            @Schema(description = "Days until expiration (negative if expired)") long daysUntilExpiration,
            @Schema(description = "Urgency level") UrgencyLevel urgencyLevel,
            @Schema(description = "Last usage timestamp") LocalDateTime lastUsed
    ) {
        // ADDED: Public getter methods for record components
        // Records automatically provide getter methods, but the error suggests
        // the compiler can't find them. These explicit methods ensure compatibility.

        public UUID getId() {
            return id;
        }

        public UUID getConnectorConfigId() {
            return connectorConfigId;
        }

        public String getCredentialType() {
            return credentialType;
        }

        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }

        public long getDaysUntilExpiration() {
            return daysUntilExpiration;
        }

        public UrgencyLevel getUrgencyLevel() {
            return urgencyLevel;
        }

        public LocalDateTime getLastUsed() {
            return lastUsed;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private UUID id;
            private UUID connectorConfigId;
            private String credentialType;
            private LocalDateTime expiresAt;
            private long daysUntilExpiration;
            private UrgencyLevel urgencyLevel;
            private LocalDateTime lastUsed;

            public Builder id(UUID id) {
                this.id = id;
                return this;
            }

            public Builder connectorConfigId(UUID connectorConfigId) {
                this.connectorConfigId = connectorConfigId;
                return this;
            }

            public Builder credentialType(String credentialType) {
                this.credentialType = credentialType;
                return this;
            }

            public Builder expiresAt(LocalDateTime expiresAt) {
                this.expiresAt = expiresAt;
                return this;
            }

            public Builder daysUntilExpiration(long daysUntilExpiration) {
                this.daysUntilExpiration = daysUntilExpiration;
                return this;
            }

            public Builder urgencyLevel(UrgencyLevel urgencyLevel) {
                this.urgencyLevel = urgencyLevel;
                return this;
            }

            public Builder lastUsed(LocalDateTime lastUsed) {
                this.lastUsed = lastUsed;
                return this;
            }

            public CredentialExpirationDto build() {
                return new CredentialExpirationDto(id, connectorConfigId, credentialType, expiresAt,
                        daysUntilExpiration, urgencyLevel, lastUsed);
            }
        }
    }

    @Schema(description = "Urgency levels for credential expiration")
    public enum UrgencyLevel {
        @Schema(description = "Immediate action required") CRITICAL,
        @Schema(description = "Action needed soon") HIGH,
        @Schema(description = "Plan for rotation") MEDIUM,
        @Schema(description = "Monitor for changes") LOW
    }
}