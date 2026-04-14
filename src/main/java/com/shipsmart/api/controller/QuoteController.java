package com.shipsmart.api.controller;

import com.shipsmart.api.auth.AuthHelper;
import com.shipsmart.api.dto.QuoteRequest;
import com.shipsmart.api.dto.QuoteResponse;
import com.shipsmart.api.service.QuoteService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
