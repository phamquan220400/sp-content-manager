package com.samuel.app.platform.dto;

/**
 * Response DTO containing YouTube OAuth authorization URL.
 * Used by GET /platforms/youtube/auth/url endpoint.
 */
public record YouTubeAuthUrlResponse(
    String authorizationUrl
) {}