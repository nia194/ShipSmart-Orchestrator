package com.shipsmart.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Create-shipment request body")
public record CreateShipmentRequest(
        @NotBlank String origin,
        @NotBlank String destination,
        @NotNull LocalDate dropOffDate,
        @NotNull LocalDate expectedDeliveryDate,
        List<PackageDto> packages,
        Double totalWeight,
        Integer totalItems
) {
    @Schema(description = "A single package in a shipment")
    public record PackageDto(Double weight, Double length, Double width, Double height, Integer quantity) {}
}
