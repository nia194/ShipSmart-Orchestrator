package com.shipsmart.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized exception handling for all REST controllers.
 * Returns standardized JSON error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(int status, String error, String message, String path, Instant timestamp) {}

    /** 400 — Bean Validation failures from @Valid. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed on {}: {}", request.getRequestURI(), message);
        return ResponseEntity.badRequest().body(
                new ErrorResponse(400, "Bad Request", message, request.getRequestURI(), Instant.now()));
    }

    /** 400 — Malformed JSON or type mismatch. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(
                new ErrorResponse(400, "Bad Request", "Malformed request body", request.getRequestURI(), Instant.now()));
    }

    /** 400 — Illegal argument. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(
                new ErrorResponse(400, "Bad Request", ex.getMessage(), request.getRequestURI(), Instant.now()));
    }

    /** 500 — Catch-all for unexpected errors. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(
                new ErrorResponse(500, "Internal Server Error", "An unexpected error occurred", request.getRequestURI(), Instant.now()));
    }
}
