package com.shipsmart.api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundReturnsProblemDetail404() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/shipments/abc");
        ResponseEntity<ProblemDetail> resp = handler.handleNotFound(
                new ResourceNotFoundException("Shipment", "abc"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ProblemDetail pd = resp.getBody();
        assertThat(pd).isNotNull();
        assertThat(pd.getTitle()).isEqualTo("Shipment not found");
        assertThat(pd.getType().toString()).endsWith("/resource-not-found");
        assertThat(pd.getInstance().toString()).isEqualTo("/api/v1/shipments/abc");
    }

    @Test
    void optimisticLockReturns409() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/shipments/abc");
        ResponseEntity<ProblemDetail> resp = handler.handleOptimisticLock(
                new OptimisticLockingFailureException("stale"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getTitle()).isEqualTo("Stale version");
    }

    @Test
    void rateLimitIncludesRetryAfter() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/shipments");
        ResponseEntity<ProblemDetail> resp = handler.handleRateLimit(
                new RateLimitExceededException(12), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resp.getHeaders().getFirst("Retry-After")).isEqualTo("12");
    }

    @Test
    void idempotencyConflictReturns422() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/shipments");
        ResponseEntity<ProblemDetail> resp = handler.handleIdempotency(
                new IdempotencyConflictException("key-1"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
