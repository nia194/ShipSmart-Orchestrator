package com.shipsmart.api.provider;

import java.math.BigDecimal;

public record ProviderQuote(
        String carrier,
        String serviceName,
        String tier,
        BigDecimal price,
        int transitDays,
        boolean guaranteed
) {}
