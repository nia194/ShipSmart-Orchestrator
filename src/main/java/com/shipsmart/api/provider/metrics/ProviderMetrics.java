package com.shipsmart.api.provider.metrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-carrier metrics recorder — counters by {@link ProviderCallOutcome}
 * plus a bounded ring buffer of the most-recent events.
 *
 * <p>Intentional concept showcase:
 * <ul>
 *   <li><b>{@link EnumMap}</b> — counters keyed by enum. EnumMap is backed
 *       by a small {@code int[]}/{@code Object[]}; far cheaper than a
 *       HashMap for enum keys. Not thread-safe by itself, so we either
 *       synchronize on the map or use {@link LongAdder} values (below).</li>
 *   <li><b>{@link ConcurrentHashMap} (carrier → stats)</b> — allows multiple
 *       carriers to update in parallel without contention. {@code computeIfAbsent}
 *       lazily creates per-carrier state on first sight.</li>
 *   <li><b>{@link ArrayDeque} as bounded ring buffer</b> — the right Deque
 *       choice for FIFO of primitives/simple objects (faster than
 *       {@code LinkedList}; the book answer for deque use cases).</li>
 * </ul>
 */
@Component
public class ProviderMetrics {

    /**
     * Per-carrier bundle. Static nested class (doesn't need the outer
     * {@code ProviderMetrics} instance) — this is the default for nested
     * classes that aren't truly inner.
     */
    public static final class CarrierStats {
        final EnumMap<ProviderCallOutcome, LongAdder> counters;
        final Deque<ProviderCallEvent> recent;
        final int recentCap;
        volatile Instant lastSeen;

        CarrierStats(int recentCap) {
            // EnumMap populated with one LongAdder per enum constant up front
            // so the update path is lock-free: `counters.get(outcome).increment()`.
            this.counters = new EnumMap<>(ProviderCallOutcome.class);
            for (ProviderCallOutcome o : ProviderCallOutcome.values()) {
                this.counters.put(o, new LongAdder());
            }
            this.recent = new ArrayDeque<>(recentCap);
            this.recentCap = recentCap;
        }
    }

    private final Map<String, CarrierStats> byCarrier = new ConcurrentHashMap<>();
    private final int recentCap;

    public ProviderMetrics(
            @Value("${shipsmart.provider-metrics.recent-events:50}") int recentCap) {
        this.recentCap = recentCap;
    }

    /**
     * Record an observation. Safe to call from any thread.
     */
    public void record(ProviderCallEvent e) {
        CarrierStats s = byCarrier.computeIfAbsent(
                e.carrier(), c -> new CarrierStats(recentCap));
        s.counters.get(e.outcome()).increment();
        s.lastSeen = e.observedAt();
        synchronized (s.recent) {
            if (s.recent.size() >= recentCap) {
                s.recent.pollFirst(); // drop oldest — classic ring buffer
            }
            s.recent.offerLast(e);
        }
    }

    // ── Read-side snapshots ──────────────────────────────────────────────────

    public Snapshot snapshot() {
        // TreeMap so carriers come out sorted alphabetically — stable output.
        Map<String, CarrierSnapshot> out = new TreeMap<>();
        for (Map.Entry<String, CarrierStats> e : byCarrier.entrySet()) {
            out.put(e.getKey(), snapshotOf(e.getValue()));
        }
        return new Snapshot(Collections.unmodifiableMap(out));
    }

    public List<ProviderCallEvent> recentEvents(String carrier) {
        CarrierStats s = byCarrier.get(carrier);
        if (s == null) return List.of();
        synchronized (s.recent) {
            // Defensive copy — List.copyOf freezes the snapshot.
            return List.copyOf(s.recent);
        }
    }

    private static CarrierSnapshot snapshotOf(CarrierStats s) {
        EnumMap<ProviderCallOutcome, Long> counts = new EnumMap<>(ProviderCallOutcome.class);
        for (ProviderCallOutcome o : ProviderCallOutcome.values()) {
            counts.put(o, s.counters.get(o).sum());
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long failures = counts.entrySet().stream()
                .filter(x -> x.getKey().isFailure())
                .mapToLong(Map.Entry::getValue).sum();
        double failureRate = total == 0 ? 0.0 : (double) failures / total;
        return new CarrierSnapshot(counts, total, failureRate, s.lastSeen);
    }

    // ── Nested value types for the read side ─────────────────────────────────

    public record CarrierSnapshot(
            Map<ProviderCallOutcome, Long> counts,
            long total,
            double failureRate,
            Instant lastSeen
    ) {}

    public record Snapshot(Map<String, CarrierSnapshot> carriers) {}
}
