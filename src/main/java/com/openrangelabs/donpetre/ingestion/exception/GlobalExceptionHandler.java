package com.openrangelabs.donpetre.ingestion.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for the ingestion service.
 *
 * <p>Provides centralized exception handling with proper HTTP status codes,
 * structured error responses, and security-conscious error messages.
 *
 * @author OpenRange Labs
 * @version 1.0
 * @since 2025-01
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles credential not found exceptions.
     */
    @ExceptionHandler(CredentialNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleCredentialNotFound(
            CredentialNotFoundException ex, ServerWebExchange exchange) {

        log.warn("Credential not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Credential Not Found")
                .message(ex.getMessage())
                .path(exchange.getRequest().getPath().toString())
                .traceId(generateTraceId())
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
    }

    /**
     * Handles credential already exists exceptions.
     */
    @ExceptionHandler(CredentialAlreadyExistsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleCredentialAlreadyExists(
            CredentialAlreadyExistsException ex, ServerWebExchange exchange) {

        log.warn("Credential already exists: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Credential Already Exists")
                .message(ex.getMessage())
                .path(exchange.getRequest().getPath().toString())
                .traceId(generateTraceId())
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(error));
    }

    /**
     * Handles credential encryption exceptions.
     */
    @ExceptionHandler(CredentialEncryptionException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleCredentialEncryption(
            CredentialEncryptionException ex, ServerWebExchange exchange) {

        log.error("Credential encryption error: {}", ex.getMessage(), ex);

        // Don't expose internal encryption details
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Credential Processing Error")
                .message("Unable to process credential. Please try again.")
                .path(exchange.getRequest().getPath().toString())
                .traceId(generateTraceId())
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
    }

    /**
     * Handles general credential exceptions.
     */
    @ExceptionHandler(CredentialException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleCredentialException(
            CredentialException ex, ServerWebExchange exchange) {

        log.error("Credential operation failed: {}", ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Credential Operation Failed")
                .message(ex.getMessage())
                .path(exchange.getRequest().getPath().toString())
                .traceId(generateTraceId())
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
    }

    /**
     * Handles validation exceptions.
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ValidationErrorResponse>> handleValidationException(
            WebExchangeBindException ex, ServerWebExchange exchange) {

        log.warn("Validation failed: {}", ex.getMessage());

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        ValidationErrorResponse error = ValidationErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Request validation failed")
                .path(exchange.getRequest().getPath().toString())
                .traceId(generateTraceId())
                .fieldErrors(fieldErrors)
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
    }

    /**
     * Handles access denied exceptions.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccessDenied(
            AccessDeniedException ex, ServerWebExchange exchange) {

        log.warn("Access denied: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Access Denied")
                .message("Insufficient privileges to access this resource")
                .path(exchange.getRequest().getPath().toString())
                .traceId(generateTraceId())
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(error));
    }

    /**
     * Handles all other exceptions.
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(
            Exception ex, ServerWebExchange exchange) {

        log.error("Unexpected error: {}", ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please try again.")
                .path(exchange.getRequest().getPath().toString())
                .traceId(generateTraceId())
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}