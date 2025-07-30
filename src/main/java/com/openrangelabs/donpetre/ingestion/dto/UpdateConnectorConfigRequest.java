package com.openrangelabs.donpetre.ingestion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating connector configurations
 */
public class UpdateConnectorConfigRequest {

    @NotNull(message = "Configuration is required")
    private JsonNode configuration;

    // Constructors
    public UpdateConnectorConfigRequest() {}

    public UpdateConnectorConfigRequest(JsonNode configuration) {
        this.configuration = configuration;
    }

    // Getters and Setters
    public JsonNode getConfiguration() { return configuration; }
    public void setConfiguration(JsonNode configuration) { this.configuration = configuration; }
}