package com.shipsmart.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * JWT authentication filter for Supabase token verification.
 * <p>
 * Extracts the user ID (sub claim) from the JWT in the Authorization header
 * and stores it in the Spring SecurityContext. Runs on every request but
 * does NOT enforce auth — that is handled by the SecurityFilterChain rules.
 * <p>
 * Supports multiple verification modes:
 * 1. JWKS (public key) — for Supabase ES256 tokens (production)
 * 2. HMAC (symmetric secret) — fallback for simple secrets
 * 3. Unsafe extraction — for local dev without secrets
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final SupabaseJwtVerifier jwtVerifier;

    public JwtAuthFilter(SupabaseJwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));
        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String userId = jwtVerifier.verifyAndExtractSubject(token);
                if (userId != null) {
                    log.debug("JWT verification successful for user={}", userId);
                    var auth = new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    log.debug("JWT verification failed (invalid/expired token)");
                }
            } else {
                log.debug("No Authorization header provided");
            }

            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            MDC.clear();
        }
    }
}
