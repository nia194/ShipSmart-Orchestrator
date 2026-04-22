package com.shipsmart.api.controller;

import com.shipsmart.api.auth.AuthHelper;
import com.shipsmart.api.dto.SavedOptionAnalyticsResponse;
import com.shipsmart.api.service.SavedOptionAnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/v1/saved-options/analytics — groupings over the authenticated
 * user's saved options. Exposes the Collections-framework showcase built
 * in {@link SavedOptionAnalyticsService}.
 */
@RestController
@RequestMapping("/api/v1/saved-options")
public class SavedOptionAnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(SavedOptionAnalyticsController.class);

    private final SavedOptionAnalyticsService service;

    public SavedOptionAnalyticsController(SavedOptionAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/analytics")
    public ResponseEntity<SavedOptionAnalyticsResponse> analytics() {
        String userId = AuthHelper.getUserId().orElseThrow();
        log.info("GET /saved-options/analytics user={}", userId);
        return ResponseEntity.ok(service.analyze(userId));
    }
}
