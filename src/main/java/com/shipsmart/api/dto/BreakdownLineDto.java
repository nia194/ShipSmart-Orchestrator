package com.shipsmart.api.dto;

/**
 * A single line item in a quote breakdown (shipping or pickup).
 */
public record BreakdownLineDto(
        String label,
        double amount
) {}
