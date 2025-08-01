package com.openrangelabs.donpetre.ingestion.exception;

/**
 * Base exception for credential-related operations.
 *
 * <p>Represents errors that occur during credential storage, retrieval,
 * rotation, or other credential management operations.
 *
 * @author OpenRange Labs
 * @version 1.0
 * @since 2025-01
 */
public class CredentialException extends RuntimeException {

    /**
     * Constructs a new credential exception with the specified detail message.
     *
     * @param message the detail message
     */
    public CredentialException(String message) {
        super(message);
    }

    /**
     * Constructs a new credential exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public CredentialException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new credential exception with the specified cause.
     *
     * @param cause the cause
     */
    public CredentialException(Throwable cause) {
        super(cause);
    }
}