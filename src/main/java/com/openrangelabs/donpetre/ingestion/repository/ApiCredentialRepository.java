package com.openrangelabs.donpetre.ingestion.repository;

import com.openrangelabs.donpetre.ingestion.entity.ApiCredential;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Repository for API credential management
 */
@Repository
public interface ApiCredentialRepository extends R2dbcRepository<ApiCredential, UUID> {

    /**
     * Find credentials by connector config ID
     */
    Flux<ApiCredential> findByConnectorConfigId(UUID connectorConfigId);

    /**
     * Find active credentials by connector config ID
     */
    @Query("SELECT * FROM api_credentials WHERE connector_config_id = :configId AND is_active = true")
    Flux<ApiCredential> findActiveByConnectorConfigId(@Param("configId") UUID connectorConfigId);

    /**
     * Find credential by connector config ID and type
     */
    Mono<ApiCredential> findByConnectorConfigIdAndCredentialType(UUID connectorConfigId, String credentialType);

    /**
     * Find active credential by connector config ID and type
     */
    @Query("""
        SELECT * FROM api_credentials 
        WHERE connector_config_id = :configId 
        AND credential_type = :type 
        AND is_active = true
        """)
    Mono<ApiCredential> findActiveByConnectorConfigIdAndCredentialType(
            @Param("configId") UUID connectorConfigId,
            @Param("type") String credentialType);

    /**
     * Find credentials expiring soon
     */
    @Query("""
        SELECT * FROM api_credentials 
        WHERE expires_at IS NOT NULL 
        AND expires_at BETWEEN NOW() AND :threshold 
        AND is_active = true
        ORDER BY expires_at ASC
        """)
    Flux<ApiCredential> findExpiringSoon(@Param("threshold") LocalDateTime threshold);

    /**
     * Find expired credentials
     */
    @Query("""
        SELECT * FROM api_credentials 
        WHERE expires_at IS NOT NULL 
        AND expires_at < NOW() 
        AND is_active = true
        """)
    Flux<ApiCredential> findExpired();

    /**
     * Update last used timestamp
     */
    @Query("UPDATE api_credentials SET last_used = NOW() WHERE id = :id")
    Mono<Integer> updateLastUsed(@Param("id") UUID id);

    /**
     * Deactivate credential
     */
    @Query("UPDATE api_credentials SET is_active = false WHERE id = :id")
    Mono<Integer> deactivate(@Param("id") UUID id);

    /**
     * Deactivate all credentials for a connector config
     */
    @Query("UPDATE api_credentials SET is_active = false WHERE connector_config_id = :configId")
    Mono<Integer> deactivateAllForConnector(@Param("configId") UUID connectorConfigId);

    /**
     * Count active credentials by type
     */
    @Query("""
        SELECT COUNT(*) FROM api_credentials 
        WHERE credential_type = :type AND is_active = true
        """)
    Mono<Long> countActiveByType(@Param("type") String credentialType);

    /**
     * Find credentials by expiration date range
     */
    @Query("""
        SELECT * FROM api_credentials 
        WHERE expires_at BETWEEN :startDate AND :endDate 
        ORDER BY expires_at ASC
        """)
    Flux<ApiCredential> findByExpirationRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get credential statistics
     */
    @Query("""
        SELECT 
            credential_type,
            COUNT(*) as total_count,
            COUNT(CASE WHEN is_active = true THEN 1 END) as active_count,
            COUNT(CASE WHEN expires_at IS NOT NULL AND expires_at < NOW() THEN 1 END) as expired_count
        FROM api_credentials 
        GROUP BY credential_type
        ORDER BY credential_type
        """)
    Flux<CredentialStats> getCredentialStats();

    /**
     * Check if credential exists by connector ID and credential name
     */
    @Query("""
        SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END 
        FROM api_credentials 
        WHERE connector_config_id = :connectorId 
        AND credential_type = :credentialName 
        AND is_active = true
        """)
    Mono<Boolean> existsByConnectorIdAndCredentialName(
            @Param("connectorId") UUID connectorId,
            @Param("credentialName") String credentialName);

    /**
     * Find credential by connector ID and credential name
     */
    @Query("""
        SELECT * FROM api_credentials 
        WHERE connector_config_id = :connectorId 
        AND credential_type = :credentialName 
        AND is_active = true
        """)
    Mono<ApiCredential> findByConnectorIdAndCredentialName(
            @Param("connectorId") UUID connectorId,
            @Param("credentialName") String credentialName);

    /**
     * Delete all credentials for a connector
     */
    @Query("DELETE FROM api_credentials WHERE connector_config_id = :connectorId")
    Mono<Long> deleteByConnectorId(@Param("connectorId") UUID connectorId);

    /**
     * Find credential usage statistics for a connector
     */
    @Query("""
        SELECT 
            credential_type as credentialName,
            last_used as lastUsed
        FROM api_credentials 
        WHERE connector_config_id = :connectorId 
        AND is_active = true
        ORDER BY last_used DESC
        """)
    Flux<CredentialUsageResult> findCredentialUsage(@Param("connectorId") UUID connectorId);

    /**
     * Interface for credential usage results
     */
    interface CredentialUsageResult {
        String getCredentialName();
        LocalDateTime getLastUsed();
    }

    /**
     * Interface for credential statistics
     */
    interface CredentialStats {
        String getCredentialType();
        Long getTotalCount();
        Long getActiveCount();
        Long getExpiredCount();
    }
}