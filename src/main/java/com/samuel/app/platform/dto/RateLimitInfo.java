package com.samuel.app.platform.dto;

import com.samuel.app.platform.adapter.PlatformType;

import java.time.LocalDateTime;

/**
 * Rate limiting information for a specific platform.
 * Contains current quota status and reset timing information.
 */
public record RateLimitInfo(
    int remainingCalls,
    int totalLimit,
    LocalDateTime resetAt,
    PlatformType platformType
) {}