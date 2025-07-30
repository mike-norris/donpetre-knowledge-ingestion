package com.openrangelabs.donpetre.ingestion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

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
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}