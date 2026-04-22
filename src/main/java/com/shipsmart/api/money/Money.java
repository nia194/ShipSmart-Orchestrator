package com.shipsmart.api.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable monetary amount — the project's sole abstraction for prices.
 *
 * <p>Design notes (intentional study vehicle for a learner):
 * <ul>
 *   <li><b>Value semantics</b> — two {@code Money} instances with equal
 *       amount and currency are {@code equals()}. Never compare money with
 *       {@code ==} in business code.</li>
 *   <li><b>Immutability</b> — all fields are {@code final}; arithmetic
 *       methods return new {@code Money} instead of mutating.</li>
 *   <li><b>Defensive construction</b> — the scale is normalized to 2 at
 *       build time so equals/hashCode can't lie about representation.</li>
 *   <li><b>Flyweight</b> — a tiny cache of common values (ZERO, whole
 *       dollars 1..100) is reused so hot paths skip allocation.</li>
 *   <li><b>Comparable</b> — natural ordering is by amount (currencies must
 *       match; otherwise throws — see {@link #compareTo(Money)}).</li>
 * </ul>
 *
 * <p>Why not a {@code record}? Records can't run custom logic that rejects
 * null/negative or normalize scale in a compact constructor cleanly AND
 * expose a flyweight factory. A {@code final class} with a private ctor
 * keeps construction under our control.
 */
public final class Money implements Comparable<Money> {

    /** Default currency — centralizing this avoids scattered string literals. */
    public static final String DEFAULT_CURRENCY = "USD";

    /** Canonical zero; reused so comparisons like {@code m.equals(Money.ZERO)} are allocation-free. */
    public static final Money ZERO = new Money(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), DEFAULT_CURRENCY);

    /**
     * Flyweight cache of whole-dollar USD values 1..100 (the range that
     * covers 90%+ of our carrier quote line items). Lookups beat
     * allocation on every hot request in the fanout path.
     *
     * <p>{@link ConcurrentHashMap} (not HashMap) because the cache is
     * populated lazily from multiple request threads. A regular
     * {@code HashMap} would race under concurrent writes.
     */
    private static final Map<Integer, Money> USD_WHOLE_DOLLAR_CACHE = new ConcurrentHashMap<>();
    static {
        // Static initializer — runs once at class load, before any Money is handed out.
        // Pre-populating avoids the "first request pays" effect in production.
        for (int i = 1; i <= 100; i++) {
            USD_WHOLE_DOLLAR_CACHE.put(i,
                    new Money(BigDecimal.valueOf(i).setScale(2, RoundingMode.HALF_UP), DEFAULT_CURRENCY));
        }
    }

    private final BigDecimal amount;
    private final String currency;

    private Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    // ── Factories ────────────────────────────────────────────────────────────
    // Multiple factories = overloading. Each one picks the cheapest available
    // construction path for its input type.

    public static Money of(BigDecimal amount) {
        return of(amount, DEFAULT_CURRENCY);
    }

    public static Money of(BigDecimal amount, String currency) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + amount);
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (DEFAULT_CURRENCY.equals(currency) && isWholeDollar(normalized)) {
            Money cached = USD_WHOLE_DOLLAR_CACHE.get(normalized.intValueExact());
            if (cached != null) return cached;
        }
        return new Money(normalized, currency);
    }

    public static Money of(double amount) {
        return of(BigDecimal.valueOf(amount));
    }

    public static Money of(long amount) {
        return of(BigDecimal.valueOf(amount));
    }

    // ── Arithmetic (returns new instances) ───────────────────────────────────

    public Money plus(Money other) {
        requireSameCurrency(other);
        return Money.of(this.amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return Money.of(this.amount.subtract(other.amount), currency);
    }

    public Money times(BigDecimal factor) {
        return Money.of(this.amount.multiply(factor), currency);
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public BigDecimal amount() { return amount; }
    public String currency()   { return currency; }
    public double toDouble()   { return amount.doubleValue(); }

    // ── Object contract ──────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return amount.compareTo(other.amount) == 0 && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        // Use stripTrailingZeros().toPlainString() so 1.00 and 1.0 hash the same.
        // (setScale(2) in factory makes this redundant, but defensive: if anyone
        // bypasses the factory via reflection, hash stays stable.)
        return Objects.hash(amount.stripTrailingZeros().toPlainString(), currency);
    }

    @Override
    public String toString() {
        return currency + " " + amount.toPlainString();
    }

    @Override
    public int compareTo(Money o) {
        requireSameCurrency(o);
        return this.amount.compareTo(o.amount);
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    private static boolean isWholeDollar(BigDecimal normalized) {
        try {
            int i = normalized.intValueExact();
            return i >= 1 && i <= 100
                    && normalized.compareTo(BigDecimal.valueOf(i).setScale(2, RoundingMode.HALF_UP)) == 0;
        } catch (ArithmeticException e) {
            return false;
        }
    }
}
