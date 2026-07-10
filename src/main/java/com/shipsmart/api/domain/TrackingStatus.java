package com.shipsmart.api.domain;

/**
 * Post-booking tracking milestones (Product Roadmap §15 P6 — tracking model).
 *
 * <p>A normalized status across carriers so the product renders one consistent timeline. Live
 * carrier codes/webhooks map onto these values [S19]; the enum is the stable seam.
 */
public enum TrackingStatus {
    LABEL_CREATED,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    EXCEPTION,
    RETURNED;

    /** Terminal statuses no longer move — the timeline is complete. */
    public boolean isTerminal() {
        return this == DELIVERED || this == RETURNED;
    }

    /** Whether this status needs user/ops attention (a delivery problem). */
    public boolean isActionable() {
        return this == EXCEPTION;
    }
}
