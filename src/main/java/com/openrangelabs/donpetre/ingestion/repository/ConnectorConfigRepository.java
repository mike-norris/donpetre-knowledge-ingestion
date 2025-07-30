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
     * Interface for connector type statistics
     */
    interface ConnectorTypeStats {
        String getConnectorType();
        Long getTotalCount();
        Long getEnabledCount();
    }
}