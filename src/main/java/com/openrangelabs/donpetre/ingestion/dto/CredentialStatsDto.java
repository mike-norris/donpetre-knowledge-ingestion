package com.openrangelabs.donpetre.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * DTO for credential statistics grouped by credential type.
 *
 * <p>Provides aggregate metrics for operational monitoring and health assessment
 * of credentials across the system.
 *
 * @author OpenRange Labs
 * @version 1.0
 * @since 2025-01
 */
@Value
@Builder
@Schema(description = "Aggregate statistics for credentials by type")
public class CredentialStatsDto {

    @Schema(description = "Type of credential", example = "api_token")
    String credentialType;

    @Schema(description = "Total number of credentials of this type", example = "15")
    long totalCount;

    @Schema(description = "Number of active (non-expired) credentials", example = "12")
    long activeCount;

    @Schema(description = "Number of expired credentials", example = "2")
    long expiredCount;

    @Schema(description = "Number of credentials expiring within 7 days", example = "1")
    long expiringSoonCount;

    @Schema(description = "Health percentage (active/total * 100)", example = "80.0")
    BigDecimal healthPercentage;

    @Schema(description = "Overall health status", example = "HEALTHY",
            allowableValues = {"HEALTHY", "WARNING", "CRITICAL"})
    String healthStatus;

    /**
     * Creates CredentialStatsDto with calculated health metrics.
     *
     * @param credentialType the type of credential
     * @param totalCount total credentials of this type
     * @param activeCount active credentials
     * @param expiredCount expired credentials
     * @param expiringSoonCount credentials expiring soon
     * @return CredentialStatsDto with calculated health status
     */
    public static CredentialStatsDto create(String credentialType, long totalCount, long activeCount,
                                            long expiredCount, long expiringSoonCount) {
        BigDecimal healthPercentage = calculateHealthPercentage(activeCount, totalCount);
        String healthStatus = determineHealthStatus(healthPercentage, expiredCount, expiringSoonCount);

        return CredentialStatsDto.builder()
                .credentialType(credentialType)
                .totalCount(totalCount)
                .activeCount(activeCount)
                .expiredCount(expiredCount)
                .expiringSoonCount(expiringSoonCount)
                .healthPercentage(healthPercentage)
                .healthStatus(healthStatus)
                .build();
    }

    private static BigDecimal calculateHealthPercentage(long activeCount, long totalCount) {
        if (totalCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(activeCount)
                .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private static String determineHealthStatus(BigDecimal healthPercentage, long expiredCount, long expiringSoonCount) {
        if (expiredCount > 0) {
            return "CRITICAL";
        }
        if (expiringSoonCount > 0 || healthPercentage.compareTo(BigDecimal.valueOf(90)) < 0) {
            return "WARNING";
        }
        return "HEALTHY";
    }
}