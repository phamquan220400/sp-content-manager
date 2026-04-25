package com.samuel.app.creator.service;

import com.samuel.app.creator.dto.CreatorProfileResponse;
import com.samuel.app.creator.dto.DashboardResponse;
import com.samuel.app.creator.dto.NavigationIcon;
import com.samuel.app.creator.dto.NavigationItem;
import com.samuel.app.creator.model.CreatorProfile.CreatorCategory;
import com.samuel.app.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private CreatorProfileService creatorProfileService;

    @InjectMocks
    private DashboardService dashboardService;

    private static final String USER_ID = "user-123";

    private CreatorProfileResponse buildProfile(String displayName, LocalDateTime createdAt) {
        return new CreatorProfileResponse(
                "profile-id",
                USER_ID,
                displayName,
                "A creative bio",
                CreatorCategory.GAMING,
                List.of("gaming"),
                Map.of("email", true),
                "uploads/profile-images/user-123/avatar.jpg",
                createdAt,
                createdAt
        );
    }

    // --- Happy path: complete profile, existing user ---

    @Test
    void getDashboardData_existingUser_returnsExistingUserWelcomeMessage() {
        LocalDateTime old = LocalDateTime.now().minusDays(30);
        when(creatorProfileService.getProfile(USER_ID)).thenReturn(buildProfile("Gamer123", old));

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        assertThat(result.welcomeMessage()).isEqualTo("Welcome back, Gamer123!");
    }

    @Test
    void getDashboardData_newUser_returnsNewUserWelcomeMessage() {
        LocalDateTime recent = LocalDateTime.now().minusDays(2);
        when(creatorProfileService.getProfile(USER_ID)).thenReturn(buildProfile("NewCreator", recent));

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        assertThat(result.welcomeMessage()).isEqualTo("Welcome to your new creator workspace, NewCreator!");
        assertThat(result.profileSummary().isNewUser()).isTrue();
    }

    @Test
    void getDashboardData_existingUser_isNewUserFalse() {
        LocalDateTime old = LocalDateTime.now().minusDays(8);
        when(creatorProfileService.getProfile(USER_ID)).thenReturn(buildProfile("OldTimer", old));

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        assertThat(result.profileSummary().isNewUser()).isFalse();
    }

    // --- Profile summary ---

    @Test
    void getDashboardData_profileSummaryBuiltCorrectly() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(30);
        when(creatorProfileService.getProfile(USER_ID)).thenReturn(buildProfile("Gamer123", createdAt));

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        assertThat(result.profileSummary().displayName()).isEqualTo("Gamer123");
        assertThat(result.profileSummary().creatorCategory()).isEqualTo("GAMING");
        assertThat(result.profileSummary().profileImageUrl())
                .isEqualTo("uploads/profile-images/user-123/avatar.jpg");
        assertThat(result.profileSummary().memberSince()).isNotNull();
        assertThat(result.profileSummary().profileCompletionStatus()).isEqualTo("COMPLETE");
    }

    @Test
    void getDashboardData_profileCompletionStatus_incomplete_whenNoBioOrCategory() {
        CreatorProfileResponse minimal = new CreatorProfileResponse(
                "p-id", USER_ID, "MinUser", null, null,
                null, null, null, LocalDateTime.now().minusDays(10), LocalDateTime.now()
        );
        when(creatorProfileService.getProfile(USER_ID)).thenReturn(minimal);

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        assertThat(result.profileSummary().profileCompletionStatus()).isEqualTo("INCOMPLETE");
    }

    @Test
    void getDashboardData_profileCompletionStatus_inProgress_whenBioSetButNoImage() {
        CreatorProfileResponse partial = new CreatorProfileResponse(
                "p-id", USER_ID, "PartUser", "My bio", CreatorCategory.TECH,
                null, null, null, LocalDateTime.now().minusDays(10), LocalDateTime.now()
        );
        when(creatorProfileService.getProfile(USER_ID)).thenReturn(partial);

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        assertThat(result.profileSummary().profileCompletionStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void getDashboardData_profileImageUrlNullWhenNotSet() {
        CreatorProfileResponse noImage = new CreatorProfileResponse(
                "p-id", USER_ID, "NoImage", "bio", CreatorCategory.GAMING,
                null, null, null, LocalDateTime.now().minusDays(5), LocalDateTime.now()
        );
        when(creatorProfileService.getProfile(USER_ID)).thenReturn(noImage);

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        assertThat(result.profileSummary().profileImageUrl()).isNull();
    }

    // --- Navigation menu ---

    @Test
    void getDashboardData_navigationMenu_containsFiveItems() {
        when(creatorProfileService.getProfile(USER_ID))
                .thenReturn(buildProfile("Nav", LocalDateTime.now().minusDays(10)));

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        assertThat(result.navigationMenu().items()).hasSize(5);
    }

    @Test
    void getDashboardData_navigationMenu_profileManagementEnabled() {
        when(creatorProfileService.getProfile(USER_ID))
                .thenReturn(buildProfile("Nav", LocalDateTime.now().minusDays(10)));

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        NavigationItem profile = findItemByIcon(result, NavigationIcon.PROFILE);
        assertThat(profile.enabled()).isTrue();
        assertThat(profile.label()).isEqualTo("Profile Management");
    }

    @Test
    void getDashboardData_navigationMenu_accountSettingsEnabled() {
        when(creatorProfileService.getProfile(USER_ID))
                .thenReturn(buildProfile("Nav", LocalDateTime.now().minusDays(10)));

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        NavigationItem settings = findItemByIcon(result, NavigationIcon.SETTINGS);
        assertThat(settings.enabled()).isTrue();
        assertThat(settings.url()).isEqualTo("/api/v1/settings");
    }

    @Test
    void getDashboardData_navigationMenu_futureEpicItemsDisabledWithStatusMessage() {
        when(creatorProfileService.getProfile(USER_ID))
                .thenReturn(buildProfile("Nav", LocalDateTime.now().minusDays(10)));

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        NavigationItem connectPlatforms = findItemByIcon(result, NavigationIcon.CONNECT_PLATFORMS);
        assertThat(connectPlatforms.enabled()).isFalse();
        assertThat(connectPlatforms.statusMessage()).isNotBlank();

        NavigationItem content = findItemByIcon(result, NavigationIcon.CONTENT);
        assertThat(content.enabled()).isFalse();
        assertThat(content.statusMessage()).isNotBlank();

        NavigationItem revenue = findItemByIcon(result, NavigationIcon.REVENUE);
        assertThat(revenue.enabled()).isFalse();
        assertThat(revenue.statusMessage()).isNotBlank();
    }

    @Test
    void getDashboardData_navigationMenu_allIconsFromAllowedSet() {
        when(creatorProfileService.getProfile(USER_ID))
                .thenReturn(buildProfile("IconTest", LocalDateTime.now().minusDays(10)));

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        Set<NavigationIcon> allowedIcons = Set.of(NavigationIcon.values());
        result.navigationMenu().items().forEach(item ->
                assertThat(allowedIcons).contains(item.icon()));
    }

    // --- Revenue placeholder ---

    @Test
    void getDashboardData_revenuePlaceholders_containEpic4Messaging() {
        when(creatorProfileService.getProfile(USER_ID))
                .thenReturn(buildProfile("Rev", LocalDateTime.now().minusDays(10)));

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        assertThat(result.revenuePlaceholder().totalRevenueMessage()).contains("Epic 4");
        assertThat(result.revenuePlaceholder().platformBreakdownMessage()).contains("Epic 4");
        assertThat(result.revenuePlaceholder().trendsMessage()).contains("Epic 4");
    }

    // --- currentDateTime ---

    @Test
    void getDashboardData_currentDateTimeIsISO8601UtcTimestamp() {
        when(creatorProfileService.getProfile(USER_ID))
                .thenReturn(buildProfile("Time", LocalDateTime.now().minusDays(10)));

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        assertThat(result.currentDateTime()).isNotNull();
        // ISO-8601 instant format ends with 'Z'
        assertThat(result.currentDateTime()).endsWith("Z");
    }

    // --- Exception handling ---

    @Test
    void getDashboardData_profileNotFound_throwsResourceNotFoundException() {
        when(creatorProfileService.getProfile(USER_ID))
                .thenThrow(new ResourceNotFoundException("Creator profile not found."));

        assertThatThrownBy(() -> dashboardService.getDashboardData(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Creator profile not found.");

        verify(creatorProfileService).getProfile(USER_ID);
    }

    // --- Account actions ---

    @Test
    void getDashboardData_accountActionsContainCorrectUrls() {
        when(creatorProfileService.getProfile(USER_ID))
                .thenReturn(buildProfile("Actions", LocalDateTime.now().minusDays(10)));

        DashboardResponse result = dashboardService.getDashboardData(USER_ID);

        assertThat(result.accountActions().accountSettingsUrl()).isEqualTo("/api/v1/settings");
        assertThat(result.accountActions().editProfileUrl()).isEqualTo("/api/v1/profile");
        assertThat(result.accountActions().logoutUrl()).isEqualTo("/auth/logout");
    }

    // --- Helper ---

    private NavigationItem findItemByIcon(DashboardResponse response, NavigationIcon icon) {
        return response.navigationMenu().items().stream()
                .filter(item -> item.icon() == icon)
                .findFirst()
                .orElseThrow(() -> new AssertionError("NavigationItem with icon " + icon + " not found"));
    }
}
