package com.samuel.app.creator.dto;

import com.samuel.app.creator.model.CreatorProfile.CreatorCategory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record CreatorProfileResponse(
    String id,
    String userId,
    String displayName,
    String bio,
    CreatorCategory creatorCategory,
    List<String> contentPreferences,
    Map<String, Object> notificationSettings,
    String profileImageUrl,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}