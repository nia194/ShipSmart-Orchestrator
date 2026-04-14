package com.shipsmart.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for POST /api/v1/quotes.
 * Mirrors the payload sent by the frontend useShippingQuotes hook.
 */
public record QuoteRequest(
        @NotBlank String origin,
        @NotBlank String destination,
        @NotBlank String dropOffDate,
        @NotBlank String expectedDeliveryDate,
        @NotEmpty @Valid List<PackageItemDto> packages
) {}
