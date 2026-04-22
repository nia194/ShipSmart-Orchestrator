package com.shipsmart.api.service;

import com.shipsmart.api.domain.SavedOption;
import com.shipsmart.api.dto.SavedOptionAnalyticsResponse;
import com.shipsmart.api.dto.SavedOptionAnalyticsResponse.TopExpensive;
import com.shipsmart.api.repository.SavedOptionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analytics over a user's saved options — intentionally a tour of the
 * Collections framework.
 *
 * <p>What each collection choice is teaching:
 * <ul>
 *   <li><b>{@link HashSet}</b> — O(1) distinctness check for "has this carrier
 *       been seen?". Order undefined, duplicates dropped, null allowed.</li>
 *   <li><b>{@link LinkedHashSet}</b> — same semantics as HashSet but keeps
 *       insertion order. Matters when "first time we saw carrier X" is
 *       meaningful to the caller.</li>
 *   <li><b>{@link TreeSet}</b> — sorted, no duplicates. Used for tiers so
 *       the response is alphabetical without a second sort pass.</li>
 *   <li><b>{@link TreeMap}</b> — sorted map. We use it both as a
 *       {@code Collectors.groupingBy} factory (for alphabetical carrier
 *       counts) and directly for {@code YearMonth → count} (chronological
 *       order falls out for free).</li>
 *   <li><b>{@link LinkedHashMap}</b> — when we want an ordering the natural
 *       sort can't produce (here: sorted by VALUE desc, not key).</li>
 *   <li><b>{@link PriorityQueue}</b> — heap. Size-capped min-heap is the
 *       streaming top-K pattern: push each element, drop the min when
 *       capacity is exceeded. O(n log k) vs O(n log n) for a full sort.</li>
 *   <li><b>{@link EnumMap}</b> — fast + compact map keyed by enum. Used for
 *       the route-frequency buckets below.</li>
 *   <li><b>{@link ArrayList}</b> — the right default random-access list.
 *       (Anywhere you see {@code List.copyOf} or {@code .toList()} we get
 *       an unmodifiable list instead — deliberate, so callers can't mutate
 *       shared response data.)</li>
 * </ul>
 *
 * <p>Why not {@link LinkedList}? LinkedList is almost never the right
 * answer in modern Java: O(n) random access, poor cache locality, and
 * {@link ArrayDeque} beats it for every queue/deque use case. Keeping
 * this note here so the learner doesn't reach for it out of habit.
 */
@Service
public class SavedOptionAnalyticsService {

    /** How many top-expensive entries to return. */
    static final int TOP_N_EXPENSIVE = 5;

    /**
     * Enum for the route frequency buckets — exercises
     * "enum as EnumMap key" (faster + more compact than HashMap of Strings).
     */
    public enum RouteFrequencyBucket {
        SINGLE,       // 1 save on this route
        OCCASIONAL,   // 2-4 saves
        FREQUENT,     // 5-9 saves
        POWER_USER;   // 10+ saves

        static RouteFrequencyBucket classify(long count) {
            if (count >= 10) return POWER_USER;
            if (count >= 5)  return FREQUENT;
            if (count >= 2)  return OCCASIONAL;
            return SINGLE;
        }
    }

    private final SavedOptionRepository repository;

    public SavedOptionAnalyticsService(SavedOptionRepository repository) {
        this.repository = repository;
    }

    public SavedOptionAnalyticsResponse analyze(String userId) {
        UUID uid = UUID.fromString(userId);
        List<SavedOption> rows = repository.findByUserId(uid);
        if (rows.isEmpty()) {
            return empty();
        }

        // ── Carriers — alphabetical via TreeMap factory in groupingBy ────────
        Map<String, Long> carriersAlpha = rows.stream()
                .collect(Collectors.groupingBy(
                        SavedOption::getCarrier,
                        TreeMap::new,               // <-- TreeMap supplier = sorted keys
                        Collectors.counting()));

        // ── Carriers — ordered by COUNT DESC, ties broken alphabetically ─────
        // A TreeMap can't sort by value; we sort entries then stream into
        // a LinkedHashMap (preserves insertion order of our streamed entries).
        Map<String, Long> carriersByCount = carriersAlpha.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,                // merge fn (unused; keys unique)
                        LinkedHashMap::new));       // <-- preserve stream order

        // ── Tiers — TreeSet for sorted distinct tiers ────────────────────────
        TreeSet<String> tierSet = rows.stream()
                .map(SavedOption::getTier)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        List<String> tiers = List.copyOf(tierSet); // unmodifiable snapshot

        // ── Distinct carriers in insertion order — LinkedHashSet ─────────────
        // HashSet would work for "distinct" but order would be unspecified.
        LinkedHashSet<String> distinctCarriersOrdered = rows.stream()
                .map(SavedOption::getCarrier)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // ── Top-N most expensive — PriorityQueue streaming top-K ─────────────
        // Min-heap by price; pop the cheapest once we exceed capacity. This
        // keeps the heap at size N and runs in O(rows · log N).
        PriorityQueue<SavedOption> topHeap = new PriorityQueue<>(
                Comparator.comparing(SavedOption::getPrice));
        for (SavedOption so : rows) {
            if (so.getPrice() == null) continue;
            topHeap.offer(so);
            if (topHeap.size() > TOP_N_EXPENSIVE) {
                topHeap.poll(); // drop the cheapest — not in top N anymore
            }
        }
        // Heap iteration order isn't sorted — drain into a list and sort DESC.
        List<TopExpensive> topExpensive = drainAndSortDesc(topHeap);

        // ── Saves per month — TreeMap<YearMonth-string, Long>, chronological ─
        TreeMap<String, Long> savesByMonth = rows.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        r -> YearMonth.from(r.getCreatedAt().atZone(ZoneId.of("UTC")))
                                .toString(), // "YYYY-MM" sorts lexicographically == chronologically
                        TreeMap::new,
                        Collectors.counting()));

        // ── Route frequency buckets — EnumMap ────────────────────────────────
        // Count saves per origin→destination, then bucket each count.
        Map<String, Long> perRoute = rows.stream()
                .collect(Collectors.groupingBy(
                        so -> so.getOrigin() + "→" + so.getDestination(),
                        Collectors.counting()));
        EnumMap<RouteFrequencyBucket, Long> buckets = new EnumMap<>(RouteFrequencyBucket.class);
        for (RouteFrequencyBucket b : RouteFrequencyBucket.values()) {
            buckets.put(b, 0L); // zero-fill so JSON shape is stable
        }
        for (long count : perRoute.values()) {
            RouteFrequencyBucket b = RouteFrequencyBucket.classify(count);
            buckets.merge(b, 1L, Long::sum);
        }
        // Convert EnumMap → LinkedHashMap<String, Long> in enum declaration order
        // so JSON keys come out as SINGLE, OCCASIONAL, FREQUENT, POWER_USER.
        LinkedHashMap<String, Long> bucketsOut = new LinkedHashMap<>();
        for (RouteFrequencyBucket b : RouteFrequencyBucket.values()) {
            bucketsOut.put(b.name(), buckets.get(b));
        }

        return new SavedOptionAnalyticsResponse(
                rows.size(),
                Collections.unmodifiableMap(carriersAlpha),
                Collections.unmodifiableMap(carriersByCount),
                tiers,
                Collections.unmodifiableSet(distinctCarriersOrdered),
                topExpensive,
                Collections.unmodifiableMap(savesByMonth),
                Collections.unmodifiableMap(bucketsOut));
    }

    private static List<TopExpensive> drainAndSortDesc(PriorityQueue<SavedOption> heap) {
        List<SavedOption> drained = new ArrayList<>(heap.size());
        while (!heap.isEmpty()) drained.add(heap.poll()); // ascending by price
        // Reverse for "most expensive first".
        drained.sort(Comparator.comparing(SavedOption::getPrice).reversed());
        List<TopExpensive> out = new ArrayList<>(drained.size());
        for (SavedOption so : drained) {
            BigDecimal p = so.getPrice() == null ? BigDecimal.ZERO : so.getPrice();
            out.add(new TopExpensive(so.getCarrier(), so.getServiceName(), p));
        }
        return List.copyOf(out);
    }

    private static SavedOptionAnalyticsResponse empty() {
        // EnumMap with zeros even on empty — stable response shape.
        LinkedHashMap<String, Long> zeroBuckets = new LinkedHashMap<>();
        for (RouteFrequencyBucket b : RouteFrequencyBucket.values()) {
            zeroBuckets.put(b.name(), 0L);
        }
        return new SavedOptionAnalyticsResponse(
                0L,
                Map.of(),
                Map.of(),
                List.of(),
                Set.of(),
                List.of(),
                Map.of(),
                Collections.unmodifiableMap(zeroBuckets));
    }
}
