package com.shipsmart.api.controller;

import com.shipsmart.api.auth.AuthHelper;
import com.shipsmart.api.dto.BookingRedirectRequest;
import com.shipsmart.api.dto.BookingRedirectResponse;
import com.shipsmart.api.service.BookingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Booking redirect API endpoint.
 * Replaces the legacy Supabase edge function: generate-book-redirect.
 * Authentication is optional — matches legacy behavior where anonymous
 * users can also trigger booking redirects.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private static final Logger log = LoggerFactory.getLogger(BookingController.class);

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * POST /api/v1/bookings/redirect
     * Tracks a booking redirect and returns the carrier checkout URL.
     */
    @PostMapping("/redirect")
    public ResponseEntity<BookingRedirectResponse> trackRedirect(@Valid @RequestBody BookingRedirectRequest body) {
        Optional<String> userId = AuthHelper.getUserId();
        log.info("POST /bookings/redirect serviceId={} user={}", body.serviceId(), userId.orElse("anonymous"));
        BookingRedirectResponse response = bookingService.trackAndRedirect(userId, body);
        return ResponseEntity.ok(response);
    }
}
