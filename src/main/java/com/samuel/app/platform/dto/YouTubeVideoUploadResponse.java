package com.samuel.app.platform.dto;

/**
 * Response DTO for YouTube video upload.
 * Contains the uploaded video's resource data from the YouTube Data API v3.
 */
public record YouTubeVideoUploadResponse(
        String videoId,
        String title,
        String privacyStatus,
        String uploadStatus,
        String videoUrl
) {}
