package com.samuel.app.shared.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-for-unit-tests-only-padding-to-256-bits-abcdef";
    private static final long EXPIRY_SECONDS = 900L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRY_SECONDS);
    }

    @Test
    void generateAccessToken_validInputs_returnsSignedJwt() {
        String token = jwtUtil.generateAccessToken("user-id-123", "user@example.com");

        assertThat(token).isNotBlank();
        // JWT format: header.payload.signature
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void parseToken_validToken_returnsClaims() {
        String token = jwtUtil.generateAccessToken("user-id-123", "user@example.com");

        Claims claims = jwtUtil.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("user-id-123");
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
    }

    @Test
    void parseToken_expiredToken_throwsJwtException() {
        // Create a JwtUtil with 0-second expiry to get an immediately expired token
        JwtUtil shortLivedUtil = new JwtUtil(SECRET, -1L);
        String expiredToken = shortLivedUtil.generateAccessToken("user-id-123", "user@example.com");

        assertThatThrownBy(() -> jwtUtil.parseToken(expiredToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseToken_tamperedToken_throwsJwtException() {
        String token = jwtUtil.generateAccessToken("user-id-123", "user@example.com");
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> jwtUtil.parseToken(tamperedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractUserId_validToken_returnsUserId() {
        String token = jwtUtil.generateAccessToken("user-id-123", "user@example.com");

        String userId = jwtUtil.extractUserId(token);

        assertThat(userId).isEqualTo("user-id-123");
    }
}
