package com.samuel.app.creator.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
