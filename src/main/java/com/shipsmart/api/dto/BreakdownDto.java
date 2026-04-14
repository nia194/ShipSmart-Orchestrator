package com.shipsmart.api.dto;

import java.util.List;

/**
 * Cost breakdown for a shipping service quote.
 */
public record BreakdownDto(
        List<BreakdownLineDto> shipping,
        List<BreakdownLineDto> pickup
) {}
