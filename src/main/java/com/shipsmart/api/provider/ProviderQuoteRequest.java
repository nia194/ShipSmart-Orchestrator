package com.shipsmart.api.provider;

import java.time.LocalDate;

public record ProviderQuoteRequest(
        String origin,
        String destination,
        LocalDate dropOffDate,
        LocalDate expectedDeliveryDate,
        double totalWeight,
        int totalItems
) {}
