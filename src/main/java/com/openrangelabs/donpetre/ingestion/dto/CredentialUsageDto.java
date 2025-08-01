package com.openrangelabs.donpetre.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for credential usage analytics and patterns.
 *
 * <p>Provides insights into how credentials are being used, helping identify
 * unused credentials and optimize rotation schedules.
 *
 * @author OpenRange Labs
 * @version 1.0
 * @since 2025-01
 */
@Value
@Builder
@Schema(description = "Credential usage analytics and patterns")
public class CredentialUsageDto {

    @Schema(description = "Credential identifier", example = "123e4567-e89b-12d3-a456-426614174000")
    UUID credentialId;

    @Schema(description = "Associated connector configuration ID", example = "456e7890-e89b-12d3-a456-426614174001")
    UUID connectorConfigId;

    @Schema(description = "Type of credential", example = "api_token")
    String credentialType;

    @Schema(description = "When the credential was created", example = "2025-01-01T00:00:00")
    LocalDateTime createdAt;

    @Schema(description = "When the credential was last used", example = "2025-01-15T10:30:00")
    LocalDateTime lastUsed;

    @Schema(description = "Days since last use (null if never used)", example = "5")
    Long daysSinceLastUse;

    @Schema(description = "Total number of times the credential has been used", example = "156")
    long usageCount;

    @Schema(description = "Average uses per day since creation", example = "3.2")
    double averageUsesPerDay;

    @Schema(description = "Usage pattern classification", example = "ACTIVE",
            allowableValues = {"ACTIVE", "OCCASIONAL", "RARELY_USED", "UNUSED"})
    String usagePattern;

    @Schema(description = "Whether the credential appears to be unused", example = "false")
    boolean isUnused;

    @Schema(description = "Recommended action based on usage pattern", example = "MONITOR",
            allowableValues = {"MONITOR", "CONSIDER_ROTATION", "CONSIDER_REMOVAL", "NO_ACTION"})
    String recommendedAction;

    /**
     * Creates CredentialUsageDto with calculated usage metrics.
     *
     * @param credentialId credential identifier
     * @param connectorConfigId connector configuration ID
     * @param credentialType credential type
     * @param createdAt creation timestamp
     * @param lastUsed last usage timestamp
     * @param usageCount total usage count
     * @return CredentialUsageDto with calculated metrics
     */
    public static CredentialUsageDto create(UUID credentialId, UUID connectorConfigId, String credentialType,
                                            LocalDateTime createdAt, LocalDateTime lastUsed, long usageCount) {
        LocalDateTime now = LocalDateTime.now();
        Long daysSinceLastUse = null;
        boolean isUnused = false;

        if (lastUsed != null) {
            daysSinceLastUse = java.time.Duration.between(lastUsed, now).toDays();
        } else {
            isUnused = true;
        }

        long daysSinceCreation = java.time.Duration.between(createdAt, now).toDays();
        double averageUsesPerDay = daysSinceCreation > 0 ? (double) usageCount / daysSinceCreation : 0;

        String usagePattern = determineUsagePattern(daysSinceLastUse, averageUsesPerDay, usageCount);
        String recommendedAction = determineRecommendedAction(usagePattern, daysSinceLastUse);

        return CredentialUsageDto.builder()
                .credentialId(credentialId)
                .connectorConfigId(connectorConfigId)
                .credentialType(credentialType)
                .createdAt(createdAt)
                .lastUsed(lastUsed)
                .daysSinceLastUse(daysSinceLastUse)
                .usageCount(usageCount)
                .averageUsesPerDay(averageUsesPerDay)
                .usagePattern(usagePattern)
                .isUnused(isUnused)
                .recommendedAction(recommendedAction)
                .build();
    }

    private static String determineUsagePattern(Long daysSinceLastUse, double averageUsesPerDay, long usageCount) {
        if (usageCount == 0) {
            return "UNUSED";
        }

        if (daysSinceLastUse == null || daysSinceLastUse > 30) {
            return "UNUSED";
        }

        if (averageUsesPerDay >= 1.0 && daysSinceLastUse <= 7) {
            return "ACTIVE";
        }

        if (averageUsesPerDay >= 0.1 && daysSinceLastUse <= 14) {
            return "OCCASIONAL";
        }

        return "RARELY_USED";
    }

    private static String determineRecommendedAction(String usagePattern, Long daysSinceLastUse) {
        return switch (usagePattern) {
            case "UNUSED" -> daysSinceLastUse != null && daysSinceLastUse > 90 ? "CONSIDER_REMOVAL" : "MONITOR";
            case "RARELY_USED" -> "CONSIDER_ROTATION";
            case "OCCASIONAL" -> "MONITOR";
            case "ACTIVE" -> "NO_ACTION";
            default -> "MONITOR";
        };
    }
}