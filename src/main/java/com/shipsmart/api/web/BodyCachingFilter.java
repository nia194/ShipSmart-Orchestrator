package com.shipsmart.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Wraps write-verb requests so the body can be re-read by idempotency hashing,
 * and wraps the response so {@link IdempotencyInterceptor} can persist the
 * captured body after the handler completes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class BodyCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String method = req.getMethod();
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            HttpServletRequest wrappedReq = new CachedBodyRequestWrapper(req);
            ContentCachingResponseWrapper wrappedRes = new ContentCachingResponseWrapper(res);
            try {
                chain.doFilter(wrappedReq, wrappedRes);
            } finally {
                wrappedRes.copyBodyToResponse();
            }
        } else {
            chain.doFilter(req, res);
        }
    }
}
