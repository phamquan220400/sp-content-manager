package com.samuel.app.creator.service;

import com.samuel.app.config.CacheConfig;
import com.samuel.app.creator.dto.AccountActions;
import com.samuel.app.creator.dto.CreatorProfileResponse;
import com.samuel.app.creator.dto.DashboardResponse;
import com.samuel.app.creator.dto.NavigationIcon;
import com.samuel.app.creator.dto.NavigationItem;
import com.samuel.app.creator.dto.NavigationMenu;
import com.samuel.app.creator.dto.ProfileSummary;
import com.samuel.app.creator.dto.RevenuePlaceholder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class DashboardService {

    private static final String COMING_EPIC_4 =
            "Data will be available when you connect platforms (Epic 4)";

    private final CreatorProfileService creatorProfileService;

    public DashboardService(CreatorProfileService creatorProfileService) {
        this.creatorProfileService = creatorProfileService;
    }

    @Cacheable(value = CacheConfig.DASHBOARD_CACHE, key = "#userId")
    public DashboardResponse getDashboardData(String userId) {
        CreatorProfileResponse profile = creatorProfileService.getProfile(userId);

        boolean isNewUser = isNewUser(profile.createdAt());
        String welcomeMessage = buildWelcomeMessage(profile.displayName(), isNewUser);
        String currentDateTime = Instant.now().toString();

        ProfileSummary profileSummary = new ProfileSummary(
                profile.displayName(),
                profile.creatorCategory() != null ? profile.creatorCategory().name() : null,
                profile.profileImageUrl(),
                profile.createdAt() != null
                        ? profile.createdAt().toInstant(ZoneOffset.UTC).toString()
                        : null,
                isNewUser,
                buildProfileCompletionStatus(profile)
        );

        NavigationMenu navigationMenu = buildNavigationMenu();

        RevenuePlaceholder revenuePlaceholder = new RevenuePlaceholder(
                COMING_EPIC_4,
                COMING_EPIC_4,
                COMING_EPIC_4
        );

        AccountActions accountActions = new AccountActions(
                "/api/v1/settings",
                "/api/v1/profile",
                "/auth/logout"
        );

        return new DashboardResponse(
                welcomeMessage,
                currentDateTime,
                profileSummary,
                navigationMenu,
                revenuePlaceholder,
                accountActions
        );
    }

    private boolean isNewUser(LocalDateTime createdAt) {
        if (createdAt == null) {
            return false;
        }
        return ChronoUnit.DAYS.between(createdAt, LocalDateTime.now(ZoneOffset.UTC)) < 7;
    }

    private String buildWelcomeMessage(String displayName, boolean isNewUser) {
        if (isNewUser) {
            return "Welcome to your new creator workspace, " + displayName + "!";
        }
        return "Welcome back, " + displayName + "!";
    }

    private String buildProfileCompletionStatus(CreatorProfileResponse profile) {
        boolean hasImage = profile.profileImageUrl() != null;
        boolean hasBio = profile.bio() != null && !profile.bio().isBlank();
        boolean hasCategory = profile.creatorCategory() != null;

        if (hasImage && hasBio && hasCategory) {
            return "COMPLETE";
        } else if (hasBio || hasCategory) {
            return "IN_PROGRESS";
        }
        return "INCOMPLETE";
    }

    private NavigationMenu buildNavigationMenu() {
        List<NavigationItem> items = List.of(
                new NavigationItem(
                        "Profile Management",
                        "/api/v1/profile",
                        true,
                        null,
                        NavigationIcon.PROFILE
                ),
                new NavigationItem(
                        "Account Settings",
                        "/api/v1/settings",
                        true,
                        null,
                        NavigationIcon.SETTINGS
                ),
                new NavigationItem(
                        "Connect Platforms",
                        null,
                        false,
                        "Coming in Epic 2",
                        NavigationIcon.CONNECT_PLATFORMS
                ),
                new NavigationItem(
                        "Content Management",
                        null,
                        false,
                        "Coming in Epic 3",
                        NavigationIcon.CONTENT
                ),
                new NavigationItem(
                        "Revenue Analytics",
                        null,
                        false,
                        "Coming in Epic 4",
                        NavigationIcon.REVENUE
                )
        );
        return new NavigationMenu(items);
    }
}
