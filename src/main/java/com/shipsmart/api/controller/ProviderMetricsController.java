package com.shipsmart.api.controller;

import com.shipsmart.api.provider.QuoteProvider;
import com.shipsmart.api.provider.QuoteProviderRegistry;
import com.shipsmart.api.provider.metrics.ProviderCallEvent;
import com.shipsmart.api.provider.metrics.ProviderMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only observability endpoints for the provider fanout layer.
 *
 * <p>GET /api/v1/providers — list of registered providers with their
 * priority + enabled flag (useful to confirm wiring at a glance).
 * <br>GET /api/v1/providers/metrics — per-carrier counters snapshot.
 * <br>GET /api/v1/providers/metrics/{carrier}/recent — last-N events.
 */
@RestController
@RequestMapping("/api/v1/providers")
public class ProviderMetricsController {

    private static final Logger log = LoggerFactory.getLogger(ProviderMetricsController.class);

    private final QuoteProviderRegistry registry;
    private final ProviderMetrics metrics;

    public ProviderMetricsController(QuoteProviderRegistry registry, ProviderMetrics metrics) {
        this.registry = registry;
        this.metrics = metrics;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listProviders() {
        List<Map<String, Object>> out = registry.all().stream()
                .map(this::toRow)
                .toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/metrics")
    public ResponseEntity<ProviderMetrics.Snapshot> snapshot() {
        return ResponseEntity.ok(metrics.snapshot());
    }

    @GetMapping("/metrics/{carrier}/recent")
    public ResponseEntity<List<ProviderCallEvent>> recent(@PathVariable String carrier) {
        log.debug("Returning recent events for carrier={}", carrier);
        return ResponseEntity.ok(metrics.recentEvents(carrier));
    }

    private Map<String, Object> toRow(QuoteProvider p) {
        // LinkedHashMap → JSON field order matches what we insert here.
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("carrier", p.carrierCode());
        row.put("enabled", p.isEnabled());
        row.put("priority", p.priority());
        return row;
    }
}
