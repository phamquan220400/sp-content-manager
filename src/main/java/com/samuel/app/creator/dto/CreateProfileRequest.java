package com.samuel.app.creator.dto;

import com.samuel.app.creator.model.CreatorProfile.CreatorCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreateProfileRequest(
    @NotBlank @Size(min = 2, max = 50) String displayName,
    @Size(max = 500) String bio,
    @NotNull CreatorCategory creatorCategory,
    @Size(max = 10) List<@Size(max = 50) String> contentPreferences,
    Map<String, Object> notificationSettings
) {}