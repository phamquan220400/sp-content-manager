package com.samuel.app.platform.dto;

import com.samuel.app.platform.adapter.PlatformType;

import java.time.LocalDateTime;

/**
 * Content metrics data fetched from a platform for a specific user.
 * Represents engagement metrics like views, likes, comments, and shares.
 */
public record ContentMetrics(
    String platformUserId,
    PlatformType platformType,
    long viewCount,
    long likeCount,
    long commentCount,
    long shareCount,
    LocalDateTime fetchedAt
) {}