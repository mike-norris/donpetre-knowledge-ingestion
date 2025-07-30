// src/main/java/com/openrangelabs/donpetre/ingestion/dto/TriggerSyncRequest.java
package com.openrangelabs.donpetre.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for triggering synchronization operations
 * Used by the IngestionController to initiate full or incremental syncs
 */
public class TriggerSyncRequest {

    @NotBlank(message = "Connector type is required")
    @Pattern(regexp = "^(github|gitlab|jira|slack)$",
            message = "Connector type must be one of: github, gitlab, jira, slack")
    private String connectorType;

    @NotBlank(message = "Configuration name is required")
    @Size(min = 1, max = 100, message = "Configuration name must be between 1 and 100 characters")
    private String configName;

    // Constructors
    public TriggerSyncRequest() {}

    public TriggerSyncRequest(String connectorType, String configName) {
        this.connectorType = connectorType;
        this.configName = configName;
    }

    // Getters and Setters
    public String getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(String connectorType) {
        this.connectorType = connectorType;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    // Helper methods

    /**
     * Check if this is a GitHub connector request
     */
    public boolean isGitHubConnector() {
        return "github".equals(connectorType);
    }

    /**
     * Check if this is a GitLab connector request
     */
    public boolean isGitLabConnector() {
        return "gitlab".equals(connectorType);
    }

    /**
     * Check if this is a Jira connector request
     */
    public boolean isJiraConnector() {
        return "jira".equals(connectorType);
    }

    /**
     * Check if this is a Slack connector request
     */
    public boolean isSlackConnector() {
        return "slack".equals(connectorType);
    }

    /**
     * Get a human-readable description of the connector type
     */
    public String getConnectorDescription() {
        return switch (connectorType) {
            case "github" -> "GitHub Repository Connector";
            case "gitlab" -> "GitLab Project Connector";
            case "jira" -> "Jira Issue Tracker Connector";
            case "slack" -> "Slack Workspace Connector";
            default -> "Unknown Connector Type";
        };
    }

    /**
     * Validate the request data
     */
    public boolean isValid() {
        return connectorType != null && !connectorType.trim().isEmpty() &&
                configName != null && !configName.trim().isEmpty() &&
                (isGitHubConnector() || isGitLabConnector() || isJiraConnector() || isSlackConnector());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TriggerSyncRequest that = (TriggerSyncRequest) o;
        return java.util.Objects.equals(connectorType, that.connectorType) &&
                java.util.Objects.equals(configName, that.configName);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(connectorType, configName);
    }

    @Override
    public String toString() {
        return "TriggerSyncRequest{" +
                "connectorType='" + connectorType + '\'' +
                ", configName='" + configName + '\'' +
                ", description='" + getConnectorDescription() + '\'' +
                '}';
    }
}