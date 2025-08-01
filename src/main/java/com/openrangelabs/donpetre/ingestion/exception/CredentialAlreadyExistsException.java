package com.openrangelabs.donpetre.ingestion.exception;

import java.util.UUID;

/**
 * Exception thrown when attempting to create a credential that already exists.
 *
 * <p>This exception indicates that a credential with the same connector configuration
 * and type combination already exists in the system.
 *
 * @author OpenRange Labs
 * @version 1.0
 * @since 2025-01
 */
public class CredentialAlreadyExistsException extends CredentialException {

    private final UUID connectorConfigId;
    private final String credentialType;

    /**
     * Constructs a new credential already exists exception.
     *
     * @param connectorConfigId the connector configuration ID
     * @param credentialType the credential type
     */
    public CredentialAlreadyExistsException(UUID connectorConfigId, String credentialType) {
        super(String.format("Credential already exists for connector %s with type: %s",
                connectorConfigId, credentialType));
        this.connectorConfigId = connectorConfigId;
        this.credentialType = credentialType;
    }

    /**
     * Constructs a new credential already exists exception with custom message.
     *
     * @param connectorConfigId the connector configuration ID
     * @param credentialType the credential type
     * @param message custom error message
     */
    public CredentialAlreadyExistsException(UUID connectorConfigId, String credentialType, String message) {
        super(message);
        this.connectorConfigId = connectorConfigId;
        this.credentialType = credentialType;
    }

    /**
     * Gets the connector configuration ID.
     *
     * @return the connector configuration ID
     */
    public UUID getConnectorConfigId() {
        return connectorConfigId;
    }

    /**
     * Gets the credential type.
     *
     * @return the credential type
     */
    public String getCredentialType() {
        return credentialType;
    }
}