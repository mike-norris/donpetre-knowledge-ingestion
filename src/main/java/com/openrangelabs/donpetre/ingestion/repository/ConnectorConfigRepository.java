package com.openrangelabs.donpetre.ingestion.repository;

import com.openrangelabs.donpetre.ingestion.entity.ConnectorConfig;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Repository for connector configuration management
 */
@Repository
public interface ConnectorConfigRepository extends R2dbcRepository<ConnectorConfig, UUID> {

    /**
     * Find configuration by connector type and name
     */
    Mono<ConnectorConfig> findByConnectorTypeAndName(String connectorType, String name);

    /**
     * Find all enabled configurations
     */
    @Query("SELECT * FROM connector_configs WHERE enabled = true ORDER BY connector_type, name")
    Flux<ConnectorConfig> findAllEnabled();

    /**
     * Find configurations by type
     */
    Flux<ConnectorConfig> findByConnectorType(String connectorType);

    /**
     * Find enabled configurations by type
     */
    @Query("SELECT * FROM connector_configs WHERE connector_type = :connectorType AND enabled = true")
    Flux<ConnectorConfig> findEnabledByConnectorType(@Param("connectorType") String connectorType);

    /**
     * Check if configuration exists by type and name
     */
    Mono<Boolean> existsByConnectorTypeAndName(String connectorType, String name);

    /**
     * Count configurations by type
     */
    @Query("SELECT COUNT(*) FROM connector_configs WHERE connector_type = :connectorType")
    Mono<Long> countByConnectorType(@Param("connectorType") String connectorType);

    /**
     * Count enabled configurations by type
     */
    @Query("SELECT COUNT(*) FROM connector_configs WHERE connector_type = :connectorType AND enabled = true")
    Mono<Long> countEnabledByConnectorType(@Param("connectorType") String connectorType);

    /**
     * Find configurations updated after a specific time
     */
    @Query("SELECT * FROM connector_configs WHERE updated_at > :since ORDER BY updated_at DESC")
    Flux<ConnectorConfig> findUpdatedSince(@Param("since") LocalDateTime since);

    /**
     * Find configurations by creator
     */
    @Query("SELECT * FROM connector_configs WHERE created_by = :createdBy ORDER BY created_at DESC")
    Flux<ConnectorConfig> findByCreatedBy(@Param("createdBy") UUID createdBy);

    /**
     * Get connector type statistics
     */
    @Query("""
        SELECT 
            connector_type,
            COUNT(*) as total_count,
            COUNT(CASE WHEN enabled = true THEN 1 END) as enabled_count
        FROM connector_configs 
        GROUP BY connector_type
        ORDER BY connector_type
        """)
    Flux<ConnectorTypeStats> getConnectorTypeStats();

    /**
     * Update enabled status
     */
    @Query("UPDATE connector_configs SET enabled = :enabled, updated_at = NOW() WHERE id = :id")
    Mono<Integer> updateEnabledStatus(@Param("id") UUID id, @Param("enabled") boolean enabled);

    /**
     * Find enabled configurations that are scheduled for sync
     */
    @Query("""
        SELECT * FROM connector_configs 
        WHERE enabled = true 
        AND (last_sync_time IS NULL OR last_sync_time < NOW() - INTERVAL '1 HOUR')
        ORDER BY COALESCE(last_sync_time, created_at) ASC
        """)
    Flux<ConnectorConfig> findEnabledConfigurationsForScheduledSync();

    /**
     * Find enabled configurations by type for scheduled sync
     */
    @Query("""
        SELECT * FROM connector_configs 
        WHERE connector_type = :connectorType 
        AND enabled = true 
        AND (last_sync_time IS NULL OR last_sync_time < NOW() - INTERVAL '1 HOUR')
        ORDER BY COALESCE(last_sync_time, created_at) ASC
        """)
    Flux<ConnectorConfig> findEnabledByConnectorTypeForScheduledSync(@Param("connectorType") String connectorType);

    /**
     * Find configurations that are due for sync based on their schedule
     */
    @Query("""
        SELECT * FROM connector_configs 
        WHERE enabled = true 
        AND (
            last_sync_time IS NULL 
            OR last_sync_time < :now - INTERVAL '1 HOUR'
        )
        ORDER BY COALESCE(last_sync_time, created_at) ASC
        """)
    Flux<ConnectorConfig> findConfigurationsDueForSync(@Param("now") LocalDateTime now);

    /**
     * Update last sync time for a configuration
     */
    @Query("UPDATE connector_configs SET last_sync_time = :lastSyncTime, updated_at = NOW() WHERE id = :id")
    Mono<Integer> updateLastSyncTime(@Param("id") UUID id, @Param("lastSyncTime") LocalDateTime lastSyncTime);

    /**
     * Find configurations with failed last sync
     */
    @Query("""
        SELECT * FROM connector_configs 
        WHERE enabled = true 
        AND consecutive_error_count > 0
        ORDER BY last_sync_time DESC
        """)
    Flux<ConnectorConfig> findFailedConfigurations();

    /**
     * Interface for connector type statistics
     */
    interface ConnectorTypeStats {
        String getConnectorType();
        Long getTotalCount();
        Long getEnabledCount();
    }
}