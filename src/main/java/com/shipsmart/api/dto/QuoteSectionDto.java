package com.shipsmart.api.dto;

import java.util.List;

/**
 * A section of quote results (top picks + more options).
 */
public record QuoteSectionDto(
        List<ShippingServiceDto> top,
        List<ShippingServiceDto> more
) {}
