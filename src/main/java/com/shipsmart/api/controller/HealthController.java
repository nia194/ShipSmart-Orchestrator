package com.shipsmart.api.controller;

import com.shipsmart.api.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check endpoint.
 * Used by Render health checks and internal monitoring.
 *
 * GET /api/v1/health  →  200 OK  {"status":"ok", ...}
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.ok());
    }
}
