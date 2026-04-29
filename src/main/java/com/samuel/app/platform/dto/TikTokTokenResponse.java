package com.samuel.app.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Internal DTO mapping the TikTok OAuth token endpoint JSON response.
 * Maps https://open.tiktokapis.com/v2/oauth/token/ response fields.
 */
public record TikTokTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("expires_in") long expiresIn,
    @JsonProperty("refresh_expires_in") long refreshExpiresIn,
    @JsonProperty("open_id") String openId,
    @JsonProperty("scope") String scope,
    @JsonProperty("token_type") String tokenType
) {}
