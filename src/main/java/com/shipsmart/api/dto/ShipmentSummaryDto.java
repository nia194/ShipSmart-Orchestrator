package com.shipsmart.api.dto;

import com.shipsmart.api.domain.ShipmentRequest;
import com.shipsmart.api.domain.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Summary view of a shipment request")
public record ShipmentSummaryDto(
        UUID id,
        String origin,
        String destination,
        LocalDate dropOffDate,
        LocalDate expectedDeliveryDate,
        Double totalWeight,
        Integer totalItems,
        ShipmentStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static ShipmentSummaryDto from(ShipmentRequest s) {
        return new ShipmentSummaryDto(
                s.getId(), s.getOrigin(), s.getDestination(),
                s.getDropOffDate(), s.getExpectedDeliveryDate(),
                s.getTotalWeight(), s.getTotalItems(), s.getStatus(),
                s.getVersion(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
