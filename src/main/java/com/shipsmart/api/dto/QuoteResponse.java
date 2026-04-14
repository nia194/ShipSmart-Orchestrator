package com.shipsmart.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for POST /api/v1/quotes.
 * Mirrors the frontend QuoteResults type in shipping-data.ts.
 * Contains two sections: prime (major carriers) and private (specialty shippers).
 *
 * Note: "private" is a Java reserved keyword, so the field is named "privateSection"
 * and serialized as "private" via @JsonProperty.
 */
public record QuoteResponse(
        QuoteSectionDto prime,
        @JsonProperty("private") QuoteSectionDto privateSection
) {}
