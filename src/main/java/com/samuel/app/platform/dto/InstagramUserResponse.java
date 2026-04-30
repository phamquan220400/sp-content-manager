package com.samuel.app.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Internal DTO mapping Meta Graph API user fields response.
 * Maps GET /{ig-user-id}?fields=id,username,followers_count response.
 */
public record InstagramUserResponse(
    @JsonProperty("id") String id,
    @JsonProperty("username") String username,
    @JsonProperty("followers_count") Long followersCount
) {}