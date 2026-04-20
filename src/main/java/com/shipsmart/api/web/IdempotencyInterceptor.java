package com.shipsmart.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipsmart.api.auth.AuthHelper;
import com.shipsmart.api.domain.IdempotencyKey;
import com.shipsmart.api.exception.IdempotencyConflictException;
import com.shipsmart.api.repository.IdempotencyKeyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Short-circuits repeated POSTs that share an Idempotency-Key.
 * Miss → handler runs, afterCompletion persists response.
 * Hit same hash → stored body is replayed.
 * Hit different hash → 422 IdempotencyConflictException.
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);
    private static final String ATTR_KEY = "shipsmart.idempotency.key";
    private static final String ATTR_HASH = "shipsmart.idempotency.hash";

    private final IdempotencyKeyRepository repo;
    private final ObjectMapper mapper;

    @Value("${shipsmart.idempotency.enabled:true}")
    private boolean enabled;

    @Value("${shipsmart.idempotency.ttl-hours:24}")
    private int ttlHours;

    public IdempotencyInterceptor(IdempotencyKeyRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if (!enabled || !(handler instanceof HandlerMethod hm)) return true;
        if (hm.getMethodAnnotation(Idempotent.class) == null) return true;

        String key = req.getHeader("Idempotency-Key");
        if (key == null || key.isBlank()) {
            res.sendError(400, "Idempotency-Key header is required");
            return false;
        }

        String bodyHash = hashBody(req);
        req.setAttribute(ATTR_KEY, key);
        req.setAttribute(ATTR_HASH, bodyHash);

        Optional<IdempotencyKey> existing = repo.findById(key);
        if (existing.isEmpty()) return true;

        IdempotencyKey row = existing.get();
        if (row.getExpiresAt().isBefore(Instant.now())) {
            repo.deleteById(key);
            return true;
        }
        if (!row.getRequestHash().equals(bodyHash)) {
            throw new IdempotencyConflictException(key);
        }

        log.info("Idempotency replay key={} path={}", key, req.getRequestURI());
        res.setStatus(row.getResponseStatus());
        res.setContentType("application/json");
        res.setHeader("Idempotency-Replay", "true");
        res.getWriter().write(row.getResponseBody());
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        if (!enabled || ex != null || !(handler instanceof HandlerMethod hm)) return;
        if (hm.getMethodAnnotation(Idempotent.class) == null) return;
        String key = (String) req.getAttribute(ATTR_KEY);
        if (key == null) return;
        if (res.getStatus() < 200 || res.getStatus() >= 300) return;

        ContentCachingResponseWrapper wrapper =
                WebUtils.getNativeResponse(res, ContentCachingResponseWrapper.class);
        String body = wrapper == null
                ? "{}"
                : new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);

        try {
            IdempotencyKey row = new IdempotencyKey();
            row.setKey(key);
            AuthHelper.getUserId().ifPresent(uid -> {
                try { row.setUserId(UUID.fromString(uid)); } catch (IllegalArgumentException ignored) {}
            });
            row.setMethod(req.getMethod());
            row.setPath(req.getRequestURI());
            row.setRequestHash((String) req.getAttribute(ATTR_HASH));
            row.setResponseStatus(res.getStatus());
            row.setResponseBody(body.isBlank() ? "{}" : body);
            row.setExpiresAt(Instant.now().plus(ttlHours, ChronoUnit.HOURS));
            repo.save(row);
        } catch (Exception e) {
            log.warn("Failed to persist idempotency record key={}: {}", key, e.getMessage());
        }
    }

    private String hashBody(HttpServletRequest req) throws Exception {
        byte[] body = new byte[0];
        if (req instanceof CachedBodyRequestWrapper c) body = c.getCachedBody();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(body);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
