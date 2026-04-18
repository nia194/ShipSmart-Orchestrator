package com.shipsmart.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * RFC 7807 ProblemDetail responses for every error path.
 * Extension members: requestId, traceId, timestamp, errors[].
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI TYPE_BASE = URI.create("https://shipsmart.dev/problems/");

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, "resource-not-found",
                ex.getResource() + " not found", ex.getMessage(), req);
    }

    @ExceptionHandler(OwnershipException.class)
    public ResponseEntity<ProblemDetail> handleOwnership(OwnershipException ex, HttpServletRequest req) {
        return problem(HttpStatus.FORBIDDEN, "ownership-denied",
                "Not owner of resource", ex.getMessage(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return problem(HttpStatus.FORBIDDEN, "access-denied", "Access denied", ex.getMessage(), req);
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ResourceConflictException ex, HttpServletRequest req) {
        return problem(HttpStatus.CONFLICT, "resource-conflict", "Conflict", ex.getMessage(), req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return problem(HttpStatus.CONFLICT, "optimistic-lock",
                "Stale version", "The resource was modified by another request. Refetch and retry.", req);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotency(IdempotencyConflictException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency-conflict",
                "Idempotency key conflict", ex.getMessage(), req);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException ex, HttpServletRequest req) {
        ResponseEntity<ProblemDetail> resp = problem(HttpStatus.TOO_MANY_REQUESTS, "rate-limit",
                "Rate limit exceeded",
                "Try again in " + ex.getRetryAfterSeconds() + "s", req);
        return ResponseEntity.status(resp.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(resp.getBody());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message",
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        ResponseEntity<ProblemDetail> resp = problem(HttpStatus.BAD_REQUEST, "validation",
                "Validation failed", "One or more fields are invalid", req);
        resp.getBody().setProperty("errors", fieldErrors);
        return resp;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "malformed-body",
                "Malformed request body", "Body could not be parsed as JSON", req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Bad request", ex.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error on {}", req.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal",
                "Internal server error", "An unexpected error occurred", req);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String slug,
                                                  String title, String detail, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(TYPE_BASE.resolve(slug));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("timestamp", Instant.now().toString());
        String requestId = MDC.get("requestId");
        if (requestId != null) pd.setProperty("requestId", requestId);
        String traceId = MDC.get("traceId");
        if (traceId != null) pd.setProperty("traceId", traceId);
        return ResponseEntity.status(status).body(pd);
    }
}
