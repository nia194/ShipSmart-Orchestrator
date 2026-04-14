package com.shipsmart.api.service.provider;

import com.shipsmart.api.dto.PackageItemDto;
import java.util.List;

/**
 * Shipment details passed to shipping providers for quote generation.
 * Normalized representation of origin, destination, dates, and packages.
 */
public record ShipmentForQuote(
        String origin,           // e.g., "New York, NY"
        String destination,      // e.g., "Los Angeles, CA"
        String dropOffDate,      // ISO date: "2026-04-15"
        String expectedDeliveryDate,  // ISO date: "2026-04-20"
        List<PackageItemDto> packages,
        double totalWeight,      // Total weight in lbs (pre-calculated)
        int totalItems           // Total item count (pre-calculated)
) {}
