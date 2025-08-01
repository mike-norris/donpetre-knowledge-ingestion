package com.openrangelabs.donpetre.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for storing encrypted API credentials.
 *
 * <p>Used by the CredentialController to securely store authentication tokens
 * and other sensitive credential information.
 *
 * <p><strong>Security Note:</strong> The 'value' field contains sensitive data and should be
 * handled with appropriate security measures (HTTPS, logging exclusion, etc.)
 *
 * @author OpenRange Labs
 * @version 1.0
 * @since 2025-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for storing encrypted API credentials")
public class StoreCredentialRequest {

    @NotNull(message = "Connector config ID is required")
    @Schema(description = "Connector configuration identifier",
            example = "123e4567-e89b-12d3-a456-426614174000", required = true)
    private UUID connectorConfigId;

    @NotBlank(message = "Credential type is required")
    @Pattern(regexp = "^(api_token|oauth_token|api_key|webhook_secret|username_password)$",
            message = "Credential type must be one of: api_token, oauth_token, api_key, webhook_secret, username_password")
    @Schema(description = "Type of credential being stored",
            example = "api_token", required = true,
            allowableValues = {"api_token", "oauth_token", "api_key", "webhook_secret", "username_password"})
    private String credentialType;

    @NotBlank(message = "Credential value is required")
    @Size(min = 8, max = 2048, message = "Credential value must be between 8 and 2048 characters")
    @Schema(description = "The credential value (will be encrypted)",
            example = "ghp_1234567890abcdefghijklmnopqrstuvwxyz", required = true)
    private String value;

    @Schema(description = "Optional expiration date for the credential",
            example = "2025-12-31T23:59:59")
    private LocalDateTime expiresAt;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @Schema(description = "Optional description of the credential",
            example = "Production API token for GitHub integration")
    private String description;

    @Size(max = 100, message = "Issuer cannot exceed 100 characters")
    @Schema(description = "Optional issuer of the credential",
            example = "GitHub")
    private String issuer;

    @Size(max = 200, message = "Scope cannot exceed 200 characters")
    @Schema(description = "Optional scope or permissions for the credential",
            example = "repo:read, user:email")
    private String scope;
}