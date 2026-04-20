package com.shipsmart.api.web;

import com.shipsmart.api.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP Bucket4j rate limiting on public POST endpoints.
 * In-memory; when Orchestrator scales past one replica, move bucket state to Redis.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${shipsmart.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${shipsmart.rate-limit.shipments-per-minute:20}")
    private int shipmentsPerMinute;

    @Value("${shipsmart.rate-limit.quotes-per-minute:30}")
    private int quotesPerMinute;

    @Value("${shipsmart.rate-limit.bookings-per-minute:10}")
    private int bookingsPerMinute;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || !"POST".equals(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }
        Integer limit = limitFor(req.getRequestURI());
        if (limit == null) {
            chain.doFilter(req, res);
            return;
        }
        String ip = clientIp(req);
        String key = ip + "|" + bucketScope(req.getRequestURI());
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            res.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(req, res);
        } else {
            long retryAfterSec = probe.getNanosToWaitForRefill() / 1_000_000_000L;
            throw new RateLimitExceededException(Math.max(1, retryAfterSec));
        }
    }

    private Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(perMinute)
                        .refillGreedy(perMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private Integer limitFor(String path) {
        if (path.startsWith("/api/v1/shipments")) return shipmentsPerMinute;
        if (path.startsWith("/api/v1/quotes")) return quotesPerMinute;
        if (path.startsWith("/api/v1/bookings")) return bookingsPerMinute;
        return null;
    }

    private String bucketScope(String path) {
        if (path.startsWith("/api/v1/shipments")) return "shipments";
        if (path.startsWith("/api/v1/quotes")) return "quotes";
        if (path.startsWith("/api/v1/bookings")) return "bookings";
        return "default";
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
