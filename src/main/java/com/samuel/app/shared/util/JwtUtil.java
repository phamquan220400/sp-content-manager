package com.samuel.app.shared.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenExpirySeconds;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${app.auth.access-token-expiry}") long accessTokenExpirySeconds) {
        // Validate JWT secret is not default value in production
        if ("changeme-in-production-use-env-var".equals(secret)) {
            throw new IllegalArgumentException(
                "JWT secret must be changed from default value. Set JWT_SECRET environment variable.");
        }
        if (secret.length() < 32) {
            throw new IllegalArgumentException(
                "JWT secret must be at least 32 characters long for HMAC-SHA256 security.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirySeconds = accessTokenExpirySeconds;
    }

    public String generateAccessToken(String userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpirySeconds)))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) throws JwtException {
        return parseToken(token).getSubject();
    }

    public long getAccessTokenExpirySeconds() {
        return accessTokenExpirySeconds;
    }
}
