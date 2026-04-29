package com.samuel.app.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Internal DTO for mapping YouTube Data API v3 channels.list response.
 * Maps JSON response from https://www.googleapis.com/youtube/v3/channels
 */
public record YouTubeChannelResponse(
    List<Item> items
) {
    
    public record Item(
        String id,
        Snippet snippet,
        Statistics statistics
    ) {}
    
    public record Snippet(
        String title
    ) {}
    
    public record Statistics(
        @JsonProperty("subscriberCount") Long subscriberCount
    ) {}
}