package com.samuel.app.creator.dto;

import com.samuel.app.creator.model.CreatorProfile.CreatorCategory;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record UpdateProfileRequest(
    @Size(min = 2, max = 50) @Pattern(regexp = "^(?!\\s*$).+", message = "must not be blank") String displayName,
    @Size(max = 500) String bio,
    CreatorCategory creatorCategory,
    @Size(max = 10) List<@Size(max = 50) String> contentPreferences,
    Map<String, Object> notificationSettings
) {}