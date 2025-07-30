// src/main/java/com/openrangelabs/donpetre/ingestion/dto/StoreCredentialRequest.java
package com.openrangelabs.donpetre.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for storing encrypted API credentials
 * Used by the CredentialController to securely store authentication tokens
 *
 * Security Note: The 'value' field contains sensitive data and should be
 * handled with appropriate security measures (HTTPS, logging exclusion, etc.)
 */
public class StoreCredentialRequest {

    @NotNull(message = "Connector config ID is required")
    private UUID connectorConfigId;

    @NotBlank(message = "Credential type is required")
    @Pattern(regexp = "^(api_token|oauth_token|api_key|webhook_secret|username_password)$",
            message = "Credential type must be one of: api_token, oauth_token, api_key, webhook_secret, username_password")
    private String credentialType;

    @NotBlank(message = "Credential value is required")
    @Size(min = 8, max = 2048, message = "Credential value must be between 8 and 2048 characters")
    private String value;

    private LocalDateTime expiresAt;

    // Optional metadata fields
    private String description;
    private String issuer;
    private String scope;

    // Constructors
    public StoreCredentialRequest() {}

    public StoreCredentialRequest(UUID connectorConfigId, String credentialType, String value) {
        this.connectorConfigId = connectorConfigId;
        this.credentialType = credentialType;
        this.value = value;
    }

    public StoreCredentialRequest(UUID connectorConfigId, String credentialType, String value, LocalDateTime expiresAt) {
        this.connectorConfigId = connectorConfigId;
        this.credentialType = credentialType;
        this.value = value;
        this.expiresAt = expiresAt;
    }

    public StoreCredentialRequest(UUID connectorConfigId, String credentialType, String value,
                                  LocalDateTime expiresAt, String description) {
        this.connectorConfigId = connectorConfigId;
        this.credentialType = credentialType;
        this.value = value;
        this.expiresAt = expiresAt;
        this.description = description;
    }

    // Getters and Setters
    public UUID getConnectorConfigId() {
        return connectorConfigId;
    }

    public void setConnectorConfigId(UUID connectorConfigId) {
        this.connectorConfigId = connectorConfigId;
    }

    public String getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(String credentialType) {
        this.credentialType = credentialType;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    // Helper methods

    /**
     * Check if this is an API token credential
     */
    public boolean isApiToken() {
        return "api_token".equals(credentialType);
    }

    /**
     * Check if this is an OAuth token credential
     */
    public boolean isOAuthToken() {
        return "oauth_token".equals(credentialType);
    }

    /**
     * Check if this is an API key credential
     */
    public boolean isApiKey() {
        return "api_key".equals(credentialType);
    }

    /**
     * Check if this is a webhook secret credential
     */
    public boolean isWebhookSecret() {
        return "webhook_secret".equals(credentialType);
    }

    /**
     * Check if this is a username/password credential
     */
    public boolean isUsernamePassword() {
        return "username_password".equals(credentialType);
    }

    /**
     * Check if the credential has an expiration date
     */
    public boolean hasExpiration() {
        return expiresAt != null;
    }

    /**
     * Check if the credential is already expired
     */
    public boolean isExpired() {
        return hasExpiration() && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if the credential expires within the specified number of days
     */
    public boolean isExpiringSoon(int daysThreshold) {
        return hasExpiration() &&
                LocalDateTime.now().plusDays(daysThreshold).isAfter(expiresAt);
    }

    /**
     * Get days until expiration (negative if already expired)
     */
    public long getDaysUntilExpiration() {
        if (!hasExpiration()) return Long.MAX_VALUE;
        return java.time.Duration.between(LocalDateTime.now(), expiresAt).toDays();
    }

    /**
     * Get a human-readable description of the credential type
     */
    public String getCredentialTypeDescription() {
        return switch (credentialType) {
            case "api_token" -> "API Token (Personal Access Token or Service Token)";
            case "oauth_token" -> "OAuth Access Token";
            case "api_key" -> "API Key";
            case "webhook_secret" -> "Webhook Secret Key";
            case "username_password" -> "Username and Password";
            default -> "Unknown Credential Type";
        };
    }

    /**
     * Validate the credential format based on type
     */
    public boolean isValidCredentialFormat() {
        if (value == null || value.trim().isEmpty()) return false;

        return switch (credentialType) {
            case "api_token" -> isValidApiTokenFormat();
            case "oauth_token" -> isValidOAuthTokenFormat();
            case "api_key" -> isValidApiKeyFormat();
            case "webhook_secret" -> isValidWebhookSecretFormat();
            case "username_password" -> isValidUsernamePasswordFormat();
            default -> false;
        };
    }

    /**
     * Get security recommendations for this credential type
     */
    public String getSecurityRecommendations() {
        return switch (credentialType) {
            case "api_token" -> "Use tokens with minimal required scopes. Set expiration dates. Rotate regularly.";
            case "oauth_token" -> "Implement proper token refresh flow. Store refresh tokens securely.";
            case "api_key" -> "Rotate keys monthly. Use environment-specific keys.";
            case "webhook_secret" -> "Use cryptographically strong secrets. Verify signatures.";
            case "username_password" -> "Use strong passwords. Enable 2FA where possible. Consider using tokens instead.";
            default -> "Follow security best practices for credential management.";
        };
    }

    /**
     * Mask the credential value for logging purposes
     */
    public String getMaskedValue() {
        if (value == null || value.length() < 8) {
            return "***";
        }

        return switch (credentialType) {
            case "api_token", "oauth_token" ->
                    value.substring(0, 8) + "..." + value.substring(value.length() - 4);
            case "api_key" ->
                    value.substring(0, 6) + "..." + value.substring(value.length() - 3);
            case "webhook_secret" ->
                    "webhook_secret_***";
            case "username_password" ->
                    "username:***";
            default -> "***";
        };
    }

    // Private validation helper methods

    private boolean isValidApiTokenFormat() {
        // GitHub: ghp_, ghq_, ghs_, gho_, ghu_
        // GitLab: glpat-
        // Generic: minimum length and pattern checks
        return value.length() >= 20 &&
                (value.startsWith("ghp_") || value.startsWith("ghq_") ||
                        value.startsWith("ghs_") || value.startsWith("gho_") ||
                        value.startsWith("ghu_") || value.startsWith("glpat-") ||
                        value.matches("^[A-Za-z0-9_-]+$"));
    }

    private boolean isValidOAuthTokenFormat() {
        // OAuth tokens are typically longer and contain specific patterns
        return value.length() >= 16 && value.matches("^[A-Za-z0-9._-]+$");
    }

    private boolean isValidApiKeyFormat() {
        // API keys vary widely, but generally alphanumeric
        return value.length() >= 16 && value.matches("^[A-Za-z0-9._-]+$");
    }

    private boolean isValidWebhookSecretFormat() {
        // Webhook secrets should be cryptographically strong
        return value.length() >= 16;
    }

    private boolean isValidUsernamePasswordFormat() {
        // Expecting format: username:password
        return value.contains(":") && value.split(":").length >= 2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StoreCredentialRequest that = (StoreCredentialRequest) o;
        return java.util.Objects.equals(connectorConfigId, that.connectorConfigId) &&
                java.util.Objects.equals(credentialType, that.credentialType);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(connectorConfigId, credentialType);
    }

    @Override
    public String toString() {
        return "StoreCredentialRequest{" +
                "connectorConfigId=" + connectorConfigId +
                ", credentialType='" + credentialType + '\'' +
                ", value='" + getMaskedValue() + '\'' +
                ", expiresAt=" + expiresAt +
                ", description='" + description + '\'' +
                ", issuer='" + issuer + '\'' +
                ", scope='" + scope + '\'' +
                ", hasExpiration=" + hasExpiration() +
                '}';
    }
}