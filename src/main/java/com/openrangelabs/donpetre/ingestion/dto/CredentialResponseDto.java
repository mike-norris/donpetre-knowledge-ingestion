package com.openrangelabs.donpetre.ingestion.dto;

import com.openrangelabs.donpetre.ingestion.entity.ApiCredential;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for credential operations.
 *
 * <p>Contains credential metadata but never the actual credential value
 * for security reasons. Includes expiration analysis and usage information.
 *
 * @author OpenRange Labs
 * @version 1.0
 * @since 2025-01
 */
@Value
@Builder
@Schema(description = "Credential metadata response (excludes actual credential value)")
public class CredentialResponseDto {

    @Schema(description = "Unique credential identifier", example = "123e4567-e89b-12d3-a456-426614174000")
    UUID id;

    @Schema(description = "Associated connector configuration ID", example = "456e7890-e89b-12d3-a456-426614174001")
    UUID connectorConfigId;

    @Schema(description = "Type of credential", example = "api_token",
            allowableValues = {"api_token", "oauth_token", "api_key", "webhook_secret", "username_password"})
    String credentialType;

    @Schema(description = "When the credential expires (null if no expiration)", example = "2025-12-31T23:59:59")
    LocalDateTime expiresAt;

    @Schema(description = "When the credential was last used", example = "2025-01-15T10:30:00")
    LocalDateTime lastUsed;

    @Schema(description = "When the credential was created", example = "2025-01-01T00:00:00")
    LocalDateTime createdAt;

    @Schema(description = "Whether the credential is currently active", example = "true")
    boolean isActive;

    @Schema(description = "Whether the credential has expired", example = "false")
    boolean isExpired;

    @Schema(description = "Whether the credential is expiring within 7 days", example = "false")
    boolean isExpiringSoon;

    @Schema(description = "Days until expiration (negative if expired, null if no expiration)", example = "45")
    Long daysUntilExpiration;

    @Schema(description = "Human-readable expiration status", example = "HEALTHY",
            allowableValues = {"HEALTHY", "EXPIRING_SOON", "EXPIRED", "NO_EXPIRATION"})
    String expirationStatus;

    @Schema(description = "Optional description of the credential", example = "Production API token for GitHub integration")
    String description;

    /**
     * Creates a CredentialResponseDto from an ApiCredential entity.
     *
     * <p>Performs expiration analysis and status calculations during conversion.
     *
     * @param credential the ApiCredential entity
     * @return CredentialResponseDto with calculated fields
     */
    public static CredentialResponseDto fromEntity(ApiCredential credential) {
        LocalDateTime now = LocalDateTime.now();
        Long daysUntilExpiration = null;
        String expirationStatus;
        boolean isExpired = false;
        boolean isExpiringSoon = false;

        if (credential.getExpiresAt() != null) {
            Duration timeUntilExpiration = Duration.between(now, credential.getExpiresAt());
            daysUntilExpiration = timeUntilExpiration.toDays();

            if (daysUntilExpiration < 0) {
                isExpired = true;
                expirationStatus = "EXPIRED";
            } else if (daysUntilExpiration <= 7) {
                isExpiringSoon = true;
                expirationStatus = "EXPIRING_SOON";
            } else {
                expirationStatus = "HEALTHY";
            }
        } else {
            expirationStatus = "NO_EXPIRATION";
        }

        return CredentialResponseDto.builder()
                .id(credential.getId())
                .connectorConfigId(credential.getConnectorConfigId())
                .credentialType(credential.getCredentialType())
                .expiresAt(credential.getExpiresAt())
                .lastUsed(credential.getLastUsed())
                .createdAt(credential.getCreatedAt())
                .isActive(credential.getIsActive())
                .isExpired(isExpired)
                .isExpiringSoon(isExpiringSoon)
                .daysUntilExpiration(daysUntilExpiration)
                .expirationStatus(expirationStatus)
                .description(credential.getDescription())
                .build();
    }
}