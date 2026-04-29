package com.samuel.app.platform.dto;

/**
 * Response DTO containing the TikTok OAuth authorization URL.
 * Returned by GET /platforms/tiktok/auth/url endpoint.
 */
public record TikTokAuthUrlResponse(String authorizationUrl) {}
