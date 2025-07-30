package com.openrangelabs.donpetre.ingestion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a knowledge source configuration
 * Links to the main knowledge_sources table from the gateway
 */
@Table("knowledge_sources")
public class KnowledgeSource {

    @Id
    private UUID id;

    private String type;
    private JsonNode configuration;

    @Column("last_sync")
    private LocalDateTime lastSync;

    @Column("is_active")
    private Boolean isActive = true;

    @Column("connector_config_id")
    private UUID connectorConfigId;

    @Column("last_ingestion_job_id")
    private UUID lastIngestionJobId;

    @Column("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column("updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Constructors
    public KnowledgeSource() {}

    public KnowledgeSource(String type, JsonNode configuration) {
        this.type = type;
        this.configuration = configuration;
    }

    public KnowledgeSource(String type, JsonNode configuration, UUID connectorConfigId) {
        this.type = type;
        this.configuration = configuration;
        this.connectorConfigId = connectorConfigId;
    }

    // Business methods
    public void updateLastSync() {
        this.lastSync = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateLastSync(LocalDateTime syncTime) {
        this.lastSync = syncTime;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateLastJob(UUID jobId) {
        this.lastIngestionJobId = jobId;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return isActive != null && isActive;
    }

    public void activate() {
        this.isActive = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasBeenSynced() {
        return lastSync != null;
    }

    public long getDaysSinceLastSync() {
        if (lastSync == null) return Long.MAX_VALUE;
        return java.time.Duration.between(lastSync, LocalDateTime.now()).toDays();
    }

    public boolean isStale(int daysThreshold) {
        return getDaysSinceLastSync() > daysThreshold;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public JsonNode getConfiguration() { return configuration; }
    public void setConfiguration(JsonNode configuration) {
        this.configuration = configuration;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getLastSync() { return lastSync; }
    public void setLastSync(LocalDateTime lastSync) { this.lastSync = lastSync; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public UUID getConnectorConfigId() { return connectorConfigId; }
    public void setConnectorConfigId(UUID connectorConfigId) { this.connectorConfigId = connectorConfigId; }

    public UUID getLastIngestionJobId() { return lastIngestionJobId; }
    public void setLastIngestionJobId(UUID lastIngestionJobId) { this.lastIngestionJobId = lastIngestionJobId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KnowledgeSource that = (KnowledgeSource) o;
        return java.util.Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id);
    }

    @Override
    public String toString() {
        return "KnowledgeSource{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", isActive=" + isActive +
                ", lastSync=" + lastSync +
                ", connectorConfigId=" + connectorConfigId +
                ", daysSinceLastSync=" + (hasBeenSynced() ? getDaysSinceLastSync() : "never") +
                '}';
    }
}