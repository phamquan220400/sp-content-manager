package com.samuel.app.platform.dto;

/**
 * Response DTO containing Instagram OAuth authorization URL.
 * Used by GET /platforms/instagram/auth/url endpoint.
 */
public record InstagramAuthUrlResponse(
    String authorizationUrl
) {}