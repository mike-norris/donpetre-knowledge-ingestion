package com.openrangelabs.donpetre.ingestion.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Standard error response structure for API errors.
 *
 * <p>Provides consistent error information across all endpoints including
 * timestamp, status code, error type, message, and request path.
 *
 * @author OpenRange Labs
 * @version 1.0
 * @since 2025-01
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response structure")
public class ErrorResponse {

    @Schema(description = "When the error occurred", example = "2025-01-15T10:30:00")
    LocalDateTime timestamp;

    @Schema(description = "HTTP status code", example = "404")
    int status;

    @Schema(description = "Error type", example = "Credential Not Found")
    String error;

    @Schema(description = "Detailed error message", example = "Credential not found with ID: 123e4567-e89b-12d3-a456-426614174000")
    String message;

    @Schema(description = "Request path that caused the error", example = "/api/credentials/123e4567-e89b-12d3-a456-426614174000")
    String path;

    @Schema(description = "Unique trace ID for debugging", example = "a1b2c3d4")
    String traceId;

    @Schema(description = "Additional error details", example = "Constraint violation details")
    String details;
}