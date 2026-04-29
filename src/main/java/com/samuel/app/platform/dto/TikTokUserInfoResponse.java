package com.samuel.app.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Internal DTO mapping the TikTok User Info API response.
 * TikTok wraps user data in a data.user envelope:
 * { "data": { "user": { "open_id": ..., "display_name": ..., "follower_count": ... } } }
 */
public record TikTokUserInfoResponse(Data data) {

    public record Data(User user) {

        public record User(
            @JsonProperty("open_id") String openId,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("follower_count") Long followerCount
        ) {}
    }
}
