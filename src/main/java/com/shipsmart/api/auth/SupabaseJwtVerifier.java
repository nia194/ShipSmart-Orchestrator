package com.shipsmart.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JWT verifier for Supabase tokens (ES256/JWKS-based).
 *
 * Supports two verification modes:
 * 1. JWKS (public key) — for Supabase ES256 tokens, fetched from issuer's JWKS endpoint
 * 2. HMAC (symmetric secret) — only for HS256-signed tokens (legacy/test mode)
 *
 * JWKS keys are cached in memory and refreshed if a key ID is not found.
 * ES256 tokens are verified exclusively with public keys; HMAC fallback only applies to HS256.
 */
@Component
public class SupabaseJwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(SupabaseJwtVerifier.class);

    @Value("${shipsmart.supabase.url:}")
    private String supabaseUrl;

    @Value("${shipsmart.supabase.jwt-secret:}")
    private String jwtSecret;

    @Value("${shipsmart.security.require-jwt-secret:false}")
    private boolean requireJwtSecret;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // JWKS cache: kid -> PublicKey
    private final Map<String, PublicKey> jwksCache = new ConcurrentHashMap<>();
    // Issuer URL cache (derived from token iss claim)
    private final Map<String, String> issuerJwksUrlCache = new ConcurrentHashMap<>();

    /**
     * Verify a JWT token and extract the subject (user ID).
     * Returns null if verification fails.
     *
     * Strategy:
     * 1. Inspect token algorithm from header
     * 2. If ES256 (ECDSA): require JWKS verification; do NOT fall back to HMAC
     * 3. If HS256 (HMAC): verify with shared secret if configured
     * 4. Otherwise: unsafe extraction (dev/test only)
     */
    public String verifyAndExtractSubject(String token) {
        try {
            String alg = extractAlgorithmFromHeader(token);
            if (alg == null) {
                log.debug("Could not determine token algorithm");
                return null;
            }

            // ES256 (ECDSA) — Supabase production tokens
            if ("ES256".equals(alg)) {
                String subject = verifyWithJwks(token);
                if (subject != null) {
                    log.debug("ES256 token verified successfully");
                    return subject;
                }
                log.warn("ES256 token verification failed (JWKS lookup or signature mismatch)");
                return null;
            }

            // HS256 (HMAC) — legacy/test tokens
            if ("HS256".equals(alg)) {
                if (jwtSecret != null && !jwtSecret.isBlank()) {
                    return verifyWithHmac(token);
                }
                log.debug("HS256 token but no secret configured; cannot verify");
                return null;
            }

            // Unknown algorithm
            log.warn("Unsupported token algorithm: {}", alg);
            return null;
        } catch (Exception e) {
            log.debug("Token extraction failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract the 'alg' claim from the JWT header.
     */
    private String extractAlgorithmFromHeader(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length < 1) {
            return null;
        }

        String headerJson = new String(
                Base64.getUrlDecoder().decode(parts[0]),
                StandardCharsets.UTF_8
        );
        Map<String, Object> header = mapper.readValue(headerJson, Map.class);
        return (String) header.get("alg");
    }

    /**
     * Verify token using JWKS (ES256).
     * Returns subject if valid, null otherwise.
     *
     * Derives JWKS URL from JWT payload's 'iss' claim (issuer),
     * e.g., iss="https://project.supabase.co/auth/v1" → JWKS at /.well-known/jwks.json
     */
    private String verifyWithJwks(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length < 3) {
            log.debug("Invalid token format (fewer than 3 parts)");
            return null;
        }

        // Extract header for kid
        String headerJson = new String(
                Base64.getUrlDecoder().decode(parts[0]),
                StandardCharsets.UTF_8
        );
        Map<String, Object> header = mapper.readValue(headerJson, Map.class);
        String kid = (String) header.get("kid");

        if (kid == null) {
            log.debug("Token header missing 'kid' — cannot verify with JWKS");
            return null;
        }

        // Extract payload for iss (issuer)
        String payloadJson = new String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8
        );
        Map<String, Object> payload = mapper.readValue(payloadJson, Map.class);
        String issuer = (String) payload.get("iss");

        if (issuer == null) {
            log.debug("Token payload missing 'iss' (issuer) — cannot determine JWKS URL");
            return null;
        }

        // Construct JWKS URL from issuer
        String jwksUrl = getJwksUrl(issuer);
        log.debug("Using JWKS URL derived from issuer: {}", jwksUrl);

        // Get public key from JWKS cache (refresh if needed)
        PublicKey publicKey = getPublicKey(kid, jwksUrl);
        if (publicKey == null) {
            log.warn("JWKS verification failed: could not find public key for kid={} from issuer={}", kid, issuer);
            return null;
        }

        log.debug("Found matching public key (kid={}, type={}); verifying ES256 signature", kid, publicKey.getAlgorithm());

        // Verify and extract claims
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            log.info("ES256 token signature verified successfully: kid={}, user={}, issuer={}", kid, claims.getSubject(), issuer);
            return claims.getSubject();
        } catch (Exception e) {
            log.warn("ES256 signature verification failed for token from issuer={}, kid={}: {}", issuer, kid, e.getMessage());
            return null;
        }
    }

    /**
     * Construct JWKS URL from issuer URL.
     * Supabase issuer format: https://project.supabase.co/auth/v1
     * JWKS URL: https://project.supabase.co/auth/v1/.well-known/jwks.json
     */
    private String getJwksUrl(String issuer) {
        return issuer.replaceAll("/$", "") + "/.well-known/jwks.json";
    }

    /**
     * Verify token using HMAC (HS256).
     * Returns subject if valid, null otherwise.
     */
    private String verifyWithHmac(String token) throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    /**
     * Decode JWT payload without signature verification.
     * Only used as last resort when neither JWKS nor secret is configured.
     */
    private String extractSubjectUnsafe(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        String payloadJson = new String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8
        );
        Map<String, Object> payload = mapper.readValue(payloadJson, Map.class);
        Object subject = payload.get("sub");
        return subject instanceof String s ? s : null;
    }

    /**
     * Get public key from JWKS cache, refreshing from the specified JWKS URL if kid not found.
     */
    private PublicKey getPublicKey(String kid, String jwksUrl) throws Exception {
        // Check cache first
        if (jwksCache.containsKey(kid)) {
            log.debug("Found kid={} in JWKS cache", kid);
            return jwksCache.get(kid);
        }

        // Fetch and cache JWKS from the specified URL
        if (!fetchAndCacheJwks(jwksUrl)) {
            return null;
        }

        return jwksCache.get(kid);
    }

    /**
     * Fetch JWKS from the specified URL and cache all public keys.
     * Supabase Auth JWKS endpoint: https://project.supabase.co/auth/v1/.well-known/jwks.json
     */
    private boolean fetchAndCacheJwks(String jwksUrl) {
        try {
            log.info("Fetching JWKS from: {}", jwksUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(jwksUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("JWKS fetch failed with HTTP {}: {}", response.statusCode(), jwksUrl);
                return false;
            }

            Map<String, Object> jwks = mapper.readValue(response.body(), Map.class);
            Object keysObj = jwks.get("keys");

            if (!(keysObj instanceof java.util.List)) {
                log.error("JWKS response missing 'keys' array from {}", jwksUrl);
                return false;
            }

            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> keys = (java.util.List<Map<String, Object>>) keysObj;

            int keyCount = 0;
            int rsaKeyCount = 0;
            int ecKeyCount = 0;

            for (Map<String, Object> keyData : keys) {
                String keyId = (String) keyData.get("kid");
                String keyType = (String) keyData.get("kty");
                String use = (String) keyData.get("use");

                if (keyId == null) {
                    log.debug("JWKS key missing 'kid' field, skipping");
                    continue;
                }

                log.debug("Processing JWKS key: kid={}, kty={}, use={}", keyId, keyType, use);

                // Accept both RSA and EC keys for signature verification
                if (keyType == null || (!keyType.equals("RSA") && !keyType.equals("EC"))) {
                    log.debug("Skipping key with unsupported type (kty={})", keyType);
                    continue;
                }

                if (!"sig".equals(use)) {
                    log.debug("Skipping key not marked for signatures (use={})", use);
                    continue;
                }

                try {
                    PublicKey publicKey = buildPublicKeyFromJwk(keyData);
                    jwksCache.put(keyId, publicKey);
                    keyCount++;

                    if ("RSA".equals(keyType)) {
                        rsaKeyCount++;
                        log.debug("Cached RSA-{} public key kid={}", keyData.get("alg"), keyId);
                    } else if ("EC".equals(keyType)) {
                        ecKeyCount++;
                        log.debug("Cached EC-{} public key kid={} curve={}", keyData.get("alg"), keyId, keyData.get("crv"));
                    }
                } catch (Exception e) {
                    log.warn("Failed to build public key for kid={} (kty={}): {}", keyId, keyType, e.getMessage());
                }
            }

            if (keyCount == 0) {
                log.error("JWKS response contained no usable signing keys");
                return false;
            }

            log.info("Successfully fetched and cached {} JWKS keys ({} RSA, {} EC) from {}", keyCount, rsaKeyCount, ecKeyCount, jwksUrl);
            return true;
        } catch (java.net.ConnectException | java.net.UnknownHostException e) {
            log.error("Network error fetching JWKS from {}: {}", jwksUrl, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Failed to fetch/parse JWKS from {}: {}", jwksUrl, e.getMessage());
            return false;
        }
    }

    /**
     * Build PublicKey from JWK (RSA or EC).
     * Supabase typically uses EC keys for ES256 tokens.
     */
    private PublicKey buildPublicKeyFromJwk(Map<String, Object> keyData) throws Exception {
        String kty = (String) keyData.get("kty");

        if ("RSA".equals(kty)) {
            return buildRsaPublicKey(keyData);
        } else if ("EC".equals(kty)) {
            return buildEcPublicKey(keyData);
        } else {
            throw new IllegalArgumentException("Unsupported key type: " + kty);
        }
    }

    /**
     * Build RSA PublicKey from JWK components (n, e).
     */
    private PublicKey buildRsaPublicKey(Map<String, Object> keyData) throws Exception {
        String n = (String) keyData.get("n");
        String e = (String) keyData.get("e");

        if (n == null || e == null) {
            throw new IllegalArgumentException("RSA JWK missing required components (n, e)");
        }

        byte[] decodedN = Base64.getUrlDecoder().decode(n);
        byte[] decodedE = Base64.getUrlDecoder().decode(e);

        java.math.BigInteger modulus = new java.math.BigInteger(1, decodedN);
        java.math.BigInteger exponent = new java.math.BigInteger(1, decodedE);

        java.security.spec.RSAPublicKeySpec spec = new java.security.spec.RSAPublicKeySpec(modulus, exponent);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(spec);
    }

    /**
     * Build EC PublicKey from JWK components (x, y, crv).
     * Supabase ES256 tokens use P-256 curve (crv="P-256").
     *
     * Uses JJWT's built-in EC key construction which handles curve mapping automatically.
     */
    private PublicKey buildEcPublicKey(Map<String, Object> keyData) throws Exception {
        String x = (String) keyData.get("x");
        String y = (String) keyData.get("y");
        String crv = (String) keyData.get("crv");

        if (x == null || y == null || crv == null) {
            throw new IllegalArgumentException("EC JWK missing required components (x, y, crv)");
        }

        byte[] decodedX = Base64.getUrlDecoder().decode(x);
        byte[] decodedY = Base64.getUrlDecoder().decode(y);

        java.math.BigInteger xPoint = new java.math.BigInteger(1, decodedX);
        java.math.BigInteger yPoint = new java.math.BigInteger(1, decodedY);

        // Map JWK curve name to Java/NIST curve name
        String curveName = mapJwkCurveToJavaName(crv);
        log.debug("Building EC public key: crv={}, java_curve={}", crv, curveName);

        // Get EC parameter spec using standard Java algorithms
        java.security.spec.ECParameterSpec ecParams = getEcParameterSpec(curveName);
        if (ecParams == null) {
            throw new IllegalArgumentException("Unsupported or unavailable EC curve: " + crv);
        }

        // Create the EC public key spec and generate the key
        java.security.spec.ECPoint ecPoint = new java.security.spec.ECPoint(xPoint, yPoint);
        java.security.spec.ECPublicKeySpec ecPublicKeySpec = new java.security.spec.ECPublicKeySpec(
                ecPoint, ecParams);

        KeyFactory factory = KeyFactory.getInstance("EC");
        log.debug("Generated EC public key for curve {}", curveName);
        return factory.generatePublic(ecPublicKeySpec);
    }

    /**
     * Map JWK curve names to Java/NIST standard names.
     */
    private String mapJwkCurveToJavaName(String jwkCurve) {
        switch (jwkCurve) {
            case "P-256":
                return "secp256r1";
            case "P-384":
                return "secp384r1";
            case "P-521":
                return "secp521r1";
            default:
                return jwkCurve;  // Fallback: use as-is
        }
    }

    /**
     * Get EC parameter spec for standard NIST curves.
     * Uses KeyPairGenerator to initialize and extract the parameter spec.
     */
    private java.security.spec.ECParameterSpec getEcParameterSpec(String curveName) {
        try {
            java.security.spec.ECGenParameterSpec ecGenSpec =
                    new java.security.spec.ECGenParameterSpec(curveName);
            java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("EC");
            kpg.initialize(ecGenSpec);

            // Extract the parameter spec from an initialized algorithm
            java.security.AlgorithmParameters params = java.security.AlgorithmParameters.getInstance("EC");
            params.init(ecGenSpec);
            return params.getParameterSpec(java.security.spec.ECParameterSpec.class);
        } catch (Exception e) {
            log.warn("Failed to get EC parameter spec for curve {}: {}", curveName, e.getMessage());
            return null;
        }
    }
}
