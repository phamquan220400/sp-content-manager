package com.samuel.app.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Internal DTO for mapping Google OAuth token endpoint response.
 * Maps JSON response from https://oauth2.googleapis.com/token
 */
public record YouTubeTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("expires_in") long expiresIn,
    @JsonProperty("token_type") String tokenType
) {}