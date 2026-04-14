package com.shipsmart.api.controller;

import com.shipsmart.api.auth.AuthHelper;
import com.shipsmart.api.dto.SaveOptionRequest;
import com.shipsmart.api.dto.SavedOptionResponse;
import com.shipsmart.api.service.SavedOptionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Saved option API endpoints.
 * All endpoints require authentication (Supabase JWT) — enforced by Spring Security.
 * Replaces legacy edge functions: save-option, get-saved-options, remove-saved-option.
 */
@RestController
@RequestMapping("/api/v1/saved-options")
public class SavedOptionController {

    private static final Logger log = LoggerFactory.getLogger(SavedOptionController.class);

    private final SavedOptionService savedOptionService;

    public SavedOptionController(SavedOptionService savedOptionService) {
        this.savedOptionService = savedOptionService;
    }

    /**
     * GET /api/v1/saved-options
     * Returns all saved options for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<List<SavedOptionResponse>> getSavedOptions() {
        String userId = AuthHelper.getUserId().orElseThrow();
        log.info("GET /saved-options user={}", userId);
        return ResponseEntity.ok(savedOptionService.listForUser(userId));
    }

    /**
     * POST /api/v1/saved-options
     * Saves a shipping option for the authenticated user.
     */
    @PostMapping
    public ResponseEntity<SavedOptionResponse> saveOption(@Valid @RequestBody SaveOptionRequest body) {
        String userId = AuthHelper.getUserId().orElseThrow();
        log.info("POST /saved-options carrier={} service={} user={}", body.carrier(), body.serviceName(), userId);
        SavedOptionResponse saved = savedOptionService.save(userId, body);
        return ResponseEntity.ok(saved);
    }

    /**
     * DELETE /api/v1/saved-options/{id}
     * Removes a saved option. User must own the option.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> removeSavedOption(@PathVariable String id) {
        String userId = AuthHelper.getUserId().orElseThrow();
        log.info("DELETE /saved-options/{} user={}", id, userId);
        boolean deleted = savedOptionService.remove(userId, id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Option not found or not owned by user"));
    }
}
