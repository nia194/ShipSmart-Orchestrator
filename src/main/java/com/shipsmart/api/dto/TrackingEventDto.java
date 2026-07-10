package com.shipsmart.api.dto;

import com.shipsmart.api.domain.TrackingStatus;

/**
 * One tracking milestone (Product Roadmap §15 P6).
 *
 * <p>A single point on the timeline: when it happened, the normalized status it represents, where,
 * and the carrier's human description.
 */
public record TrackingEventDto(
        String timestamp, // ISO-8601
        TrackingStatus status,
        String location,
        String description) {}
