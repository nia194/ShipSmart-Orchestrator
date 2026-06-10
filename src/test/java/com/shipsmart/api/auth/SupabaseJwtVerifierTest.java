package com.shipsmart.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link SupabaseJwtVerifier}'s HS256 path — the symmetric mode the local stack +
 * ShipSmart-Test e2e use (the e2e mints HS256 tokens with a shared secret). Verifies the happy path
 * plus every rejection: expired, wrong secret, missing subject, no-secret-configured, and a
 * malformed token. Also checks the ES256 branch self-rejects offline when the header has no kid (so
 * no network call escapes a unit test).
 */
class SupabaseJwtVerifierTest {

    // >= 32 bytes: JJWT requires a 256-bit key for HS256.
    private static final String SECRET = "e2e-test-secret-please-change-32chars-minimum";

    private SupabaseJwtVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new SupabaseJwtVerifier();
        ReflectionTestUtils.setField(verifier, "jwtSecret", SECRET);
    }

    private static String sign(String secret, String subject, Date exp) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        var b = Jwts.builder().issuedAt(new Date());
        if (subject != null) b = b.subject(subject);
        if (exp != null) b = b.expiration(exp);
        return b.signWith(key).compact();
    }

    private static Date inMinutes(long mins) {
        return new Date(System.currentTimeMillis() + mins * 60_000);
    }

    @Test
    void valid_hs256_token_yields_subject() {
        String token = sign(SECRET, "user-123", inMinutes(60));
        assertThat(verifier.verifyAndExtractSubject(token)).isEqualTo("user-123");
    }

    @Test
    void expired_token_is_rejected() {
        String token = sign(SECRET, "user-123", inMinutes(-1)); // already expired
        assertThat(verifier.verifyAndExtractSubject(token)).isNull();
    }

    @Test
    void wrong_secret_signature_is_rejected() {
        String token =
                sign("a-totally-different-secret-32-bytes-minimum!", "user-123", inMinutes(60));
        assertThat(verifier.verifyAndExtractSubject(token)).isNull();
    }

    @Test
    void token_without_subject_yields_null() {
        String token = sign(SECRET, null, inMinutes(60));
        assertThat(verifier.verifyAndExtractSubject(token)).isNull();
    }

    @Test
    void hs256_token_with_no_secret_configured_is_rejected() {
        ReflectionTestUtils.setField(verifier, "jwtSecret", "");
        String token = sign(SECRET, "user-123", inMinutes(60));
        assertThat(verifier.verifyAndExtractSubject(token)).isNull();
    }

    @Test
    void malformed_token_yields_null() {
        assertThat(verifier.verifyAndExtractSubject("not-a-jwt")).isNull();
    }

    @Test
    void es256_without_kid_rejects_offline() {
        // Header claims ES256 but carries no kid → JWKS path bails before any
        // network fetch and returns null.
        String header = b64url("{\"alg\":\"ES256\"}");
        String payload = b64url("{\"sub\":\"user-123\"}");
        String token = header + "." + payload + ".c2ln"; // dummy signature
        assertThat(verifier.verifyAndExtractSubject(token)).isNull();
    }

    private static String b64url(String json) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
