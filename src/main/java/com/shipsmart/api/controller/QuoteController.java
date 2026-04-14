package com.shipsmart.api.controller;

import com.shipsmart.api.auth.AuthHelper;
import com.shipsmart.api.dto.QuoteRequest;
import com.shipsmart.api.dto.QuoteResponse;
import com.shipsmart.api.dto.ShippingServiceDto;
import com.shipsmart.api.service.QuoteService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quote API endpoints.
 * Owns quote generation. Saved options are handled by {@link SavedOptionController}.
 *
 * Service boundary: Java owns quotes as the system-of-record.
 * FastAPI may assist with AI-ranked recommendations but does NOT write quote records.
 */
@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    private static final Logger log = LoggerFactory.getLogger(QuoteController.class);

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    /**
     * POST /api/v1/quotes
     * Generate shipping quotes for a shipment request.
     * Replaces the legacy Supabase edge function "get-shipping-quotes".
     *
     * Auth is optional for this endpoint — anonymous users can get quotes.
     * If a valid JWT is present, the userId is attributed to the shipment request.
     */
    @PostMapping
    public ResponseEntity<QuoteResponse> generateQuotes(@Valid @RequestBody QuoteRequest request) {
        String userId = AuthHelper.getUserId().orElse(null);
        log.info("POST /quotes origin={} dest={} packages={} user={}",
                request.origin(), request.destination(), request.packages().size(),
                userId != null ? userId : "anonymous");
        QuoteResponse response = quoteService.generateQuotes(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/quotes?shipmentRequestId={id}
     * Re-generate quotes for an existing shipment request.
     * Used by the Python AI/advisory service to fetch service options for scoring.
     *
     * Returns a flat list of all services (prime + private) under a "services" key.
     */
    @GetMapping
    public ResponseEntity<?> getQuotesByShipmentRequestId(
            @RequestParam UUID shipmentRequestId) {
        log.info("GET /quotes shipmentRequestId={}", shipmentRequestId);
        QuoteResponse response = quoteService.regenerateQuotes(shipmentRequestId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        List<ShippingServiceDto> services = new ArrayList<>();
        if (response.prime() != null) {
            if (response.prime().top() != null) services.addAll(response.prime().top());
            if (response.prime().more() != null) services.addAll(response.prime().more());
        }
        if (response.privateSection() != null) {
            if (response.privateSection().top() != null) services.addAll(response.privateSection().top());
            if (response.privateSection().more() != null) services.addAll(response.privateSection().more());
        }
        return ResponseEntity.ok(Map.of("services", services));
    }
}
