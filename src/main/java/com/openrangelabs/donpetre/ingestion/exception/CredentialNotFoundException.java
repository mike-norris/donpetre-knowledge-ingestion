package com.openrangelabs.donpetre.ingestion.exception;

import java.util.UUID;

/**
 * Exception thrown when a requested credential cannot be found.
 *
 * <p>This exception indicates that a credential with the specified ID
 * or connector/type combination does not exist in the system.
 *
 * @author OpenRange Labs
 * @version 1.0
 * @since 2025-01
 */
public class CredentialNotFoundException extends CredentialException {

    private final UUID credentialId;
    private final UUID connectorConfigId;
    private final String credentialType;

    /**
     * Constructs a new credential not found exception for a specific credential ID.
     *
     * @param credentialId the ID of the credential that was not found
     */
    public CredentialNotFoundException(UUID credentialId) {
        super(String.format("Credential not found with ID: %s", credentialId));
        this.credentialId = credentialId;
        this.connectorConfigId = null;
        this.credentialType = null;
    }

    /**
     * Constructs a new credential not found exception for a connector/type combination.
     *
     * @param connectorConfigId the connector configuration ID
     * @param credentialType the credential type
     */
    public CredentialNotFoundException(UUID connectorConfigId, String credentialType) {
        super(String.format("Credential not found for connector %s with type: %s", connectorConfigId, credentialType));
        this.credentialId = null;
        this.connectorConfigId = connectorConfigId;
        this.credentialType = credentialType;
    }

    /**
     * Gets the credential ID that was not found.
     *
     * @return the credential ID, or null if not applicable
     */
    public UUID getCredentialId() {
        return credentialId;
    }

    /**
     * Gets the connector configuration ID.
     *
     * @return the connector configuration ID, or null if not applicable
     */
    public UUID getConnectorConfigId() {
        return connectorConfigId;
    }

    /**
     * Gets the credential type.
     *
     * @return the credential type, or null if not applicable
     */
    public String getCredentialType() {
        return credentialType;
    }
}