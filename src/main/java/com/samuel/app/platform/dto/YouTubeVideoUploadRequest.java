package com.samuel.app.platform.dto;

import java.util.List;

/**
 * Request DTO for YouTube video upload.
 * Maps to the YouTube Data API v3 videos.insert snippet and status parts.
 */
public record YouTubeVideoUploadRequest(
        String title,
        String description,
        String categoryId,
        String privacyStatus,
        List<String> tags
) {}
