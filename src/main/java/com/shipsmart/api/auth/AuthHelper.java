package com.shipsmart.api.auth;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Utility to retrieve the authenticated userId from the SecurityContext. Works in tandem with
 * {@link JwtAuthFilter}.
 */
public final class AuthHelper {

    private AuthHelper() {}

    /**
     * Returns the authenticated user ID from the SecurityContext, or empty if not authenticated.
     */
    public static Optional<String> getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof String s && !s.isBlank()) {
            return Optional.of(s);
        }
        if (principal instanceof UserDetails ud) {
            return Optional.of(ud.getUsername());
        }
        return Optional.empty();
    }
}
