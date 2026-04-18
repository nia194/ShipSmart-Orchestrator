package com.shipsmart.api.dto;

import com.shipsmart.api.domain.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Partial update — only non-null fields are applied")
public record PatchShipmentRequest(
        String origin,
        String destination,
        LocalDate dropOffDate,
        LocalDate expectedDeliveryDate,
        Double totalWeight,
        Integer totalItems,
        ShipmentStatus status
) {}
