package com.shipsmart.api.provider.metrics;

/**
 * Outcome of a single provider call.
 *
 * <p>Study notes:
 * <ul>
 *   <li>Enum constants can carry per-constant behavior. Each constant
 *       overrides {@link #isFailure()}; this is a compact way to keep
 *       policy attached to the value.</li>
 *   <li>Enums are natural keys for {@link java.util.EnumMap}, which is
 *       faster and more compact than {@link java.util.HashMap} when the
 *       key type is an enum (see {@link ProviderMetrics}).</li>
 * </ul>
 */
public enum ProviderCallOutcome {
    SUCCESS  { @Override public boolean isFailure() { return false; } },
    TIMEOUT  { @Override public boolean isFailure() { return true; } },
    ERROR    { @Override public boolean isFailure() { return true; } },
    DISABLED { @Override public boolean isFailure() { return false; } };

    /** Per-constant method — overridden in each enum body. */
    public abstract boolean isFailure();
}
