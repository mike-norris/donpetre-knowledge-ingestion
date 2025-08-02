package com.openrangelabs.donpetre.ingestion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing connector configuration stored in the database
 * Contains configuration settings for external service connectors
 */
@Table("connector_configs")
public class ConnectorConfig {

    @Id
    private UUID id;

    @Column("connector_type")
    private String connectorType;

    private String name;
    private Boolean enabled = false;
    private JsonNode configuration;

    @Column("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column("updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column("created_by")
    private UUID createdBy;

    // ADDED: Last sync time for incremental sync support
    @Column("last_sync_time")
    private LocalDateTime lastSyncTime;

    // ADDED: Last sync cursor for connector-specific sync state
    @Column("last_sync_cursor")
    private String lastSyncCursor;

    // ADDED: Sync interval in minutes for scheduled sync
    @Column("sync_interval_minutes")
    private Integer syncIntervalMinutes = 60; // Default 1 hour

    // ADDED: Last successful sync completion time
    @Column("last_successful_sync")
    private LocalDateTime lastSuccessfulSync;

    // ADDED: Error count for monitoring connector health
    @Column("consecutive_error_count")
    private Integer consecutiveErrorCount = 0;

    // ADDED: Last error message for troubleshooting
    @Column("last_error_message")
    private String lastErrorMessage;

    // ADDED: Last error time
    @Column("last_error_time")
    private LocalDateTime lastErrorTime;

    // Constructors
    public ConnectorConfig() {}

    public ConnectorConfig(String connectorType, String name, JsonNode configuration) {
        this.connectorType = connectorType;
        this.name = name;
        this.configuration = configuration;
    }

    public ConnectorConfig(String connectorType, String name, JsonNode configuration, UUID createdBy) {
        this.connectorType = connectorType;
        this.name = name;
        this.configuration = configuration;
        this.createdBy = createdBy;
    }

    // Business methods
    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    public void enable() {
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void disable() {
        this.enabled = false;
        this.updatedAt = LocalDateTime.now();
    }

    public String getFullName() {
        return connectorType + "/" + name;
    }

    public boolean isGitHubConnector() {
        return "github".equals(connectorType);
    }

    public boolean isGitLabConnector() {
        return "gitlab".equals(connectorType);
    }

    public boolean isJiraConnector() {
        return "jira".equals(connectorType);
    }

    public boolean isSlackConnector() {
        return "slack".equals(connectorType);
    }

    // ADDED: Sync management methods
    public void updateLastSync(LocalDateTime syncTime, String cursor) {
        this.lastSyncTime = syncTime;
        this.lastSyncCursor = cursor;
        this.lastSuccessfulSync = syncTime;
        this.consecutiveErrorCount = 0; // Reset error count on successful sync
        this.lastErrorMessage = null;
        this.lastErrorTime = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateLastSync(LocalDateTime syncTime) {
        updateLastSync(syncTime, null);
    }

    public void recordSyncError(String errorMessage) {
        this.consecutiveErrorCount = (this.consecutiveErrorCount != null) ?
                this.consecutiveErrorCount + 1 : 1;
        this.lastErrorMessage = errorMessage;
        this.lastErrorTime = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean shouldRunScheduledSync() {
        if (!isEnabled() || syncIntervalMinutes == null || syncIntervalMinutes <= 0) {
            return false;
        }

        if (lastSyncTime == null) {
            return true; // Never synced before
        }

        LocalDateTime nextScheduledSync = lastSyncTime.plusMinutes(syncIntervalMinutes);
        return LocalDateTime.now().isAfter(nextScheduledSync);
    }

    public Duration getTimeSinceLastSync() {
        if (lastSyncTime == null) {
            return null;
        }
        return Duration.between(lastSyncTime, LocalDateTime.now());
    }

    public boolean hasRecentSyncErrors() {
        return consecutiveErrorCount != null && consecutiveErrorCount > 0;
    }

    public boolean isHealthy() {
        // Consider unhealthy if more than 3 consecutive errors
        return consecutiveErrorCount == null || consecutiveErrorCount <= 3;
    }

    public LocalDateTime getNextScheduledSync() {
        if (lastSyncTime == null || syncIntervalMinutes == null) {
            return null;
        }
        return lastSyncTime.plusMinutes(syncIntervalMinutes);
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getConnectorType() { return connectorType; }
    public void setConnectorType(String connectorType) { this.connectorType = connectorType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }

    public JsonNode getConfiguration() { return configuration; }
    public void setConfiguration(JsonNode configuration) {
        this.configuration = configuration;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    // ADDED: New field getters and setters
    public LocalDateTime getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(LocalDateTime lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
        this.updatedAt = LocalDateTime.now();
    }

    public String getLastSyncCursor() { return lastSyncCursor; }
    public void setLastSyncCursor(String lastSyncCursor) {
        this.lastSyncCursor = lastSyncCursor;
        this.updatedAt = LocalDateTime.now();
    }

    public Integer getSyncIntervalMinutes() { return syncIntervalMinutes; }
    public void setSyncIntervalMinutes(Integer syncIntervalMinutes) {
        this.syncIntervalMinutes = syncIntervalMinutes;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getLastSuccessfulSync() { return lastSuccessfulSync; }
    public void setLastSuccessfulSync(LocalDateTime lastSuccessfulSync) {
        this.lastSuccessfulSync = lastSuccessfulSync;
        this.updatedAt = LocalDateTime.now();
    }

    public Integer getConsecutiveErrorCount() { return consecutiveErrorCount; }
    public void setConsecutiveErrorCount(Integer consecutiveErrorCount) {
        this.consecutiveErrorCount = consecutiveErrorCount;
        this.updatedAt = LocalDateTime.now();
    }

    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getLastErrorTime() { return lastErrorTime; }
    public void setLastErrorTime(LocalDateTime lastErrorTime) {
        this.lastErrorTime = lastErrorTime;
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectorConfig that = (ConnectorConfig) o;
        return java.util.Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ConnectorConfig{" +
                "id=" + id +
                ", connectorType='" + connectorType + '\'' +
                ", name='" + name + '\'' +
                ", enabled=" + enabled +
                ", lastSyncTime=" + lastSyncTime +
                ", syncIntervalMinutes=" + syncIntervalMinutes +
                ", consecutiveErrorCount=" + consecutiveErrorCount +
                ", healthy=" + isHealthy() +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}