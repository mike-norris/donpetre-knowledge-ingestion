package com.openrangelabs.donpetre.ingestion.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing encrypted API credentials for external services
 * All credential values are encrypted using AES-256 before storage
 */
@Table("api_credentials")
public class ApiCredential {

    @Id
    private UUID id;

    @Column("connector_config_id")
    private UUID connectorConfigId;

    @Column("credential_type")
    private String credentialType;

    @Column("encrypted_value")
    private String encryptedValue;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column("last_used")
    private LocalDateTime lastUsed;

    @Column("is_active")
    private Boolean isActive = true;

    // ADD THIS FIELD - Missing description field
    @Column("description")
    private String description;

    // Constructors
    public ApiCredential() {}

    public ApiCredential(UUID connectorConfigId, String credentialType, String encryptedValue) {
        this.connectorConfigId = connectorConfigId;
        this.credentialType = credentialType;
        this.encryptedValue = encryptedValue;
    }

    public ApiCredential(UUID connectorConfigId, String credentialType, String encryptedValue, LocalDateTime expiresAt) {
        this.connectorConfigId = connectorConfigId;
        this.credentialType = credentialType;
        this.encryptedValue = encryptedValue;
        this.expiresAt = expiresAt;
    }

    // Business methods
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isExpiringSoon(int days) {
        if (expiresAt == null) return false;
        return LocalDateTime.now().plusDays(days).isAfter(expiresAt) && !isExpired();
    }

    public long getDaysUntilExpiration() {
        if (expiresAt == null) return Long.MAX_VALUE;
        return Duration.between(LocalDateTime.now(), expiresAt).toDays();
    }

    public void markAsUsed() {
        this.lastUsed = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getConnectorConfigId() { return connectorConfigId; }
    public void setConnectorConfigId(UUID connectorConfigId) { this.connectorConfigId = connectorConfigId; }

    public String getCredentialType() { return credentialType; }
    public void setCredentialType(String credentialType) { this.credentialType = credentialType; }

    public String getEncryptedValue() { return encryptedValue; }
    public void setEncryptedValue(String encryptedValue) { this.encryptedValue = encryptedValue; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastUsed() { return lastUsed; }
    public void setLastUsed(LocalDateTime lastUsed) { this.lastUsed = lastUsed; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    // ADD THESE METHODS - Missing description getter/setter
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiCredential that = (ApiCredential) o;
        return java.util.Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ApiCredential{" +
                "id=" + id +
                ", connectorConfigId=" + connectorConfigId +
                ", credentialType='" + credentialType + '\'' +
                ", expiresAt=" + expiresAt +
                ", isActive=" + isActive +
                ", expired=" + isExpired() +
                ", daysUntilExpiration=" + (expiresAt != null ? getDaysUntilExpiration() : "never") +
                '}';
    }
}