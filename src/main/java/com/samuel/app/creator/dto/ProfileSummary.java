package com.samuel.app.creator.dto;

public record ProfileSummary(
        String displayName,
        String creatorCategory,
        String profileImageUrl,
        String memberSince,
        boolean isNewUser,
        String profileCompletionStatus
) {}
