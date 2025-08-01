package com.openrangelabs.donpetre.ingestion.exception;

/**
 * Exception thrown when credential encryption or decryption operations fail.
 *
 * <p>This exception indicates a problem with the cryptographic operations
 * used to secure credential storage and retrieval.
 *
 * @author OpenRange Labs
 * @version 1.0
 * @since 2025-01
 */
public class CredentialEncryptionException extends CredentialException {

    private final String operation;

    /**
     * Constructs a new credential encryption exception.
     *
     * @param message the detail message
     */
    public CredentialEncryptionException(String message) {
        super(message);
        this.operation = "unknown";
    }

    /**
     * Constructs a new credential encryption exception with operation context.
     *
     * @param operation the operation that failed (e.g., "encryption", "decryption")
     * @param message the detail message
     */
    public CredentialEncryptionException(String operation, String message) {
        super(String.format("Credential %s failed: %s", operation, message));
        this.operation = operation;
    }

    /**
     * Constructs a new credential encryption exception with operation context and cause.
     *
     * @param operation the operation that failed
     * @param message the detail message
     * @param cause the underlying cause
     */
    public CredentialEncryptionException(String operation, String message, Throwable cause) {
        super(String.format("Credential %s failed: %s", operation, message), cause);
        this.operation = operation;
    }

    /**
     * Gets the operation that failed.
     *
     * @return the operation name
     */
    public String getOperation() {
        return operation;
    }
}