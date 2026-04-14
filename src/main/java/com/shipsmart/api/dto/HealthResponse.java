package com.shipsmart.api.dto;

import java.time.Instant;

/**
 * Response body for GET /api/v1/health
 */
public record HealthResponse(
        String status,
        String service,
        String version,
        Instant timestamp
) {
    public static HealthResponse ok() {
        return new HealthResponse("ok", "shipsmart-api-java", "0.1.0", Instant.now());
    }
}
