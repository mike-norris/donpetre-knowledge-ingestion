package com.openrangelabs.donpetre.ingestion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating connector configurations
 */
public class CreateConnectorConfigRequest {

    @NotBlank(message = "Connector type is required")
    @Pattern(regexp = "^(github|gitlab|jira|slack)$",
            message = "Connector type must be one of: github, gitlab, jira, slack")
    private String connectorType;

    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    private String name;

    @NotNull(message = "Configuration is required")
    private JsonNode configuration;

    // Constructors
    public CreateConnectorConfigRequest() {}

    public CreateConnectorConfigRequest(String connectorType, String name, JsonNode configuration) {
        this.connectorType = connectorType;
        this.name = name;
        this.configuration = configuration;
    }

    // Getters and Setters
    public String getConnectorType() { return connectorType; }
    public void setConnectorType(String connectorType) { this.connectorType = connectorType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public JsonNode getConfiguration() { return configuration; }
    public void setConfiguration(JsonNode configuration) { this.configuration = configuration; }
}