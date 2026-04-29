package com.samuel.app.platform.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for platform connection status.
 * Used by GET /platforms/youtube/connection endpoint and disconnect operations.
 */
public record PlatformConnectionResponse(
    String platformType,
    String status,
    String platformUserId,
    String platformName,
    Long followerCount,
    LocalDateTime lastSyncAt,
    LocalDateTime connectedAt
) {}