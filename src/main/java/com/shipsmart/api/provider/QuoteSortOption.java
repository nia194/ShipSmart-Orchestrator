package com.shipsmart.api.provider;

import java.util.Comparator;

/**
 * Sort policy for surfacing {@link ProviderQuote}s to the caller.
 *
 * <p>Study note — <b>enum with per-constant behavior</b>:
 * each enum constant overrides {@link #comparator()} with a different
 * strategy. This is the "enum as mini state machine / strategy dispatch"
 * pattern. The API is type-safe (no stringly-typed sort modes) and adding
 * a new option becomes impossible to forget about — the enum won't
 * compile without an implementation.
 */
public enum QuoteSortOption {
    CHEAPEST {
        @Override public Comparator<ProviderQuote> comparator() {
            return QuoteComparators.BY_PRICE_ASC;
        }
    },
    FASTEST {
        @Override public Comparator<ProviderQuote> comparator() {
            return QuoteComparators.BY_TRANSIT_ASC;
        }
    },
    GUARANTEED_FIRST {
        @Override public Comparator<ProviderQuote> comparator() {
            return QuoteComparators.GUARANTEED_THEN_PRICE;
        }
    },
    RECOMMENDED {
        @Override public Comparator<ProviderQuote> comparator() {
            // Slight preference for speed; tweak once we have real signal.
            return QuoteComparators.balanced(1.0, 8.0);
        }
    };

    public abstract Comparator<ProviderQuote> comparator();
}
