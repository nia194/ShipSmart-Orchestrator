package com.shipsmart.api.controller;

import com.shipsmart.api.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Root-level health check endpoint.
 * Allows Render (or other systems) to hit /health for health checks,
 * without requiring the /api/v1 prefix.
 *
 * GET /health  →  200 OK  {"status":"ok", ...}
 */
@RestController
public class RootHealthController {

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.ok());
    }
}
