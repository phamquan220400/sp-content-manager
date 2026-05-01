package com.samuel.app.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FacebookPageDetailsResponse(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("fan_count") Long fanCount,
    @JsonProperty("category") String category
) {
}