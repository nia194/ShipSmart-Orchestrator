package com.shipsmart.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A single package in a quote request.
 * Mirrors the frontend PackageItem type in shipping-data.ts.
 * All fields are strings to match the frontend contract.
 */
public record PackageItemDto(
        @NotBlank String type,
        @NotBlank String qty,
        @NotBlank String weight,
        @NotBlank String l,
        @NotBlank String w,
        @NotBlank String h,
        @NotNull String handling
) {}
