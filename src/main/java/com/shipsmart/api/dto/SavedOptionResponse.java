package com.shipsmart.api.dto;

/**
 * Response body for a saved option.
 * Matches the frontend SavedOption interface in useSavedOptions.ts.
 *
 * Field names use the exact camelCase keys the frontend expects:
 * id, svcId, svc, origin, dest, dropDate, delivDate, pkgSummary, bookUrl, savedAt
 */
public record SavedOptionResponse(
        String id,
        String svcId,
        ShippingServiceDto svc,
        String origin,
        String dest,
        String dropDate,
        String delivDate,
        String pkgSummary,
        String bookUrl,
        String savedAt
) {}
