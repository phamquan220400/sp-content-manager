package com.samuel.app.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Internal DTO mapping Meta Graph API token endpoint JSON response.
 * Maps both short-lived and long-lived token exchange responses.
 */
public record InstagramTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") Long expiresIn
) {}