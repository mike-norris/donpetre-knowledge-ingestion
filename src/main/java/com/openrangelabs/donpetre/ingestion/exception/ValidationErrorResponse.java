package com.openrangelabs.donpetre.ingestion.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Validation error response with field-specific error details.
 *
 * <p>Extends the standard error response to include detailed field validation
 * errors, helping clients understand exactly which fields failed validation.
 *
 * @author OpenRange Labs
 * @version 1.0
 * @since 2025-01
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Validation error response with field details")
public class ValidationErrorResponse {

    @Schema(description = "When the error occurred", example = "2025-01-15T10:30:00")
    LocalDateTime timestamp;

    @Schema(description = "HTTP status code", example = "400")
    int status;

    @Schema(description = "Error type", example = "Validation Failed")
    String error;

    @Schema(description = "General error message", example = "Request validation failed")
    String message;

    @Schema(description = "Request path that caused the error", example = "/api/credentials")
    String path;

    @Schema(description = "Unique trace ID for debugging", example = "a1b2c3d4")
    String traceId;

    @Schema(description = "Field-specific validation errors",
            example = "{\"credentialType\": \"must match pattern\", \"value\": \"size must be between 8 and 2048\"}")
    Map<String, String> fieldErrors;
}