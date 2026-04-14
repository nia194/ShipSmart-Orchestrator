package com.shipsmart.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for POST /api/v1/bookings/redirect.
 * Matches the payload sent by the legacy generate-book-redirect edge function.
 */
public record BookingRedirectRequest(
        @NotBlank @Size(max = 100) String serviceId,
        @Size(max = 100) String carrier,
        @Size(max = 100) String serviceName,
        @NotBlank @Size(max = 2000) String redirectUrl,
        @Size(max = 200) String origin,
        @Size(max = 200) String destination
) {}
