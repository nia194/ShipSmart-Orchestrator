package com.shipsmart.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Honors inbound X-Request-Id and W3C traceparent; mints them if absent.
 * Runs before JwtAuthFilter so requestId is in MDC for every log line.
 * Outbound RestTemplate/WebClient interceptors forward the same headers
 * so Python / MCP see the same correlation IDs (logs grep cleanly across
 * services).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_TRACEPARENT = "traceparent";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String requestId = req.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        String traceparent = req.getHeader(HEADER_TRACEPARENT);
        String traceId;
        String spanId;
        if (traceparent != null && traceparent.matches("[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")) {
            String[] parts = traceparent.split("-");
            traceId = parts[1];
            spanId = parts[2];
        } else {
            traceId = randomHex(32);
            spanId = randomHex(16);
            traceparent = "00-" + traceId + "-" + spanId + "-01";
        }

        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_SPAN_ID, spanId);
        res.setHeader(HEADER_REQUEST_ID, requestId);
        res.setHeader(HEADER_TRACEPARENT, traceparent);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
        }
    }

    private static String randomHex(int chars) {
        String hex = UUID.randomUUID().toString().replace("-", "");
        while (hex.length() < chars) hex += UUID.randomUUID().toString().replace("-", "");
        return hex.substring(0, chars);
    }
}
