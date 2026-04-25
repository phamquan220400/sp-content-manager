package com.samuel.app.creator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.app.creator.dto.AccountActions;
import com.samuel.app.creator.dto.DashboardResponse;
import com.samuel.app.creator.dto.NavigationIcon;
import com.samuel.app.creator.dto.NavigationItem;
import com.samuel.app.creator.dto.NavigationMenu;
import com.samuel.app.creator.dto.ProfileSummary;
import com.samuel.app.creator.dto.RevenuePlaceholder;
import com.samuel.app.creator.service.DashboardService;
import com.samuel.app.exceptions.ResourceNotFoundException;
import com.samuel.app.shared.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DashboardController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DashboardService dashboardService;

    private DashboardResponse sampleDashboard;

    @BeforeEach
    void setUp() {
        ProfileSummary profileSummary = new ProfileSummary(
                "TestCreator",
                "GAMING",
                "uploads/profile-images/test-user/avatar.jpg",
                "2025-01-01T00:00:00Z",
                false,
                "COMPLETE"
        );

        List<NavigationItem> navItems = List.of(
                new NavigationItem("Profile Management", "/api/v1/profile", true, null, NavigationIcon.PROFILE),
                new NavigationItem("Account Settings", "/api/v1/settings", true, null, NavigationIcon.SETTINGS),
                new NavigationItem("Connect Platforms", null, false, "Coming in Epic 2", NavigationIcon.CONNECT_PLATFORMS),
                new NavigationItem("Content Management", null, false, "Coming in Epic 3", NavigationIcon.CONTENT),
                new NavigationItem("Revenue Analytics", null, false, "Coming in Epic 4", NavigationIcon.REVENUE)
        );

        sampleDashboard = new DashboardResponse(
                "Welcome back, TestCreator!",
                "2025-04-25T00:00:00Z",
                profileSummary,
                new NavigationMenu(navItems),
                new RevenuePlaceholder(
                        "Data will be available when you connect platforms (Epic 4)",
                        "Data will be available when you connect platforms (Epic 4)",
                        "Data will be available when you connect platforms (Epic 4)"
                ),
                new AccountActions("/api/v1/settings", "/api/v1/profile", "/auth/logout")
        );
    }

    // --- GET /api/v1/dashboard ---

    @Test
    @WithMockUser(username = "test-user-id")
    void getDashboard_authenticated_returns200WithDashboardResponse() throws Exception {
        when(dashboardService.getDashboardData("test-user-id")).thenReturn(sampleDashboard);

        mockMvc.perform(get("/api/v1/dashboard").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.welcomeMessage").value("Welcome back, TestCreator!"))
                .andExpect(jsonPath("$.data.currentDateTime").value("2025-04-25T00:00:00Z"))
                .andExpect(jsonPath("$.data.profileSummary.displayName").value("TestCreator"));
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void getDashboard_returnsProfileSummaryWithAllFields() throws Exception {
        when(dashboardService.getDashboardData("test-user-id")).thenReturn(sampleDashboard);

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileSummary.displayName").value("TestCreator"))
                .andExpect(jsonPath("$.data.profileSummary.creatorCategory").value("GAMING"))
                .andExpect(jsonPath("$.data.profileSummary.profileImageUrl").value("uploads/profile-images/test-user/avatar.jpg"))
                .andExpect(jsonPath("$.data.profileSummary.memberSince").value("2025-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.data.profileSummary.profileCompletionStatus").value("COMPLETE"));
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void getDashboard_returnsNavigationMenuWithCorrectEpicStatuses() throws Exception {
        when(dashboardService.getDashboardData("test-user-id")).thenReturn(sampleDashboard);

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                // Profile Management — active
                .andExpect(jsonPath("$.data.navigationMenu.items[0].label").value("Profile Management"))
                .andExpect(jsonPath("$.data.navigationMenu.items[0].enabled").value(true))
                // Account Settings — active
                .andExpect(jsonPath("$.data.navigationMenu.items[1].label").value("Account Settings"))
                .andExpect(jsonPath("$.data.navigationMenu.items[1].enabled").value(true))
                // Connect Platforms — disabled
                .andExpect(jsonPath("$.data.navigationMenu.items[2].label").value("Connect Platforms"))
                .andExpect(jsonPath("$.data.navigationMenu.items[2].enabled").value(false))
                .andExpect(jsonPath("$.data.navigationMenu.items[2].statusMessage").value("Coming in Epic 2"))
                // Content Management — disabled
                .andExpect(jsonPath("$.data.navigationMenu.items[3].label").value("Content Management"))
                .andExpect(jsonPath("$.data.navigationMenu.items[3].enabled").value(false))
                .andExpect(jsonPath("$.data.navigationMenu.items[3].statusMessage").value("Coming in Epic 3"))
                // Revenue Analytics — disabled
                .andExpect(jsonPath("$.data.navigationMenu.items[4].label").value("Revenue Analytics"))
                .andExpect(jsonPath("$.data.navigationMenu.items[4].enabled").value(false))
                .andExpect(jsonPath("$.data.navigationMenu.items[4].statusMessage").value("Coming in Epic 4"));
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void getDashboard_navigationIconsAreFromAllowedSet() throws Exception {
        when(dashboardService.getDashboardData("test-user-id")).thenReturn(sampleDashboard);
        String[] allowedIcons = {"PROFILE", "SETTINGS", "LOGOUT", "CONNECT_PLATFORMS", "CONTENT", "REVENUE"};

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.navigationMenu.items[0].icon", is(in(allowedIcons))))
                .andExpect(jsonPath("$.data.navigationMenu.items[1].icon", is(in(allowedIcons))))
                .andExpect(jsonPath("$.data.navigationMenu.items[2].icon", is(in(allowedIcons))))
                .andExpect(jsonPath("$.data.navigationMenu.items[3].icon", is(in(allowedIcons))))
                .andExpect(jsonPath("$.data.navigationMenu.items[4].icon", is(in(allowedIcons))));
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void getDashboard_returnsRevenuePlaceholdersWithEpic4Messaging() throws Exception {
        when(dashboardService.getDashboardData("test-user-id")).thenReturn(sampleDashboard);

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revenuePlaceholder.totalRevenueMessage",
                        containsString("Epic 4")))
                .andExpect(jsonPath("$.data.revenuePlaceholder.platformBreakdownMessage",
                        containsString("Epic 4")))
                .andExpect(jsonPath("$.data.revenuePlaceholder.trendsMessage",
                        containsString("Epic 4")));
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void getDashboard_returnsAccountActionsUrls() throws Exception {
        when(dashboardService.getDashboardData("test-user-id")).thenReturn(sampleDashboard);

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountActions.accountSettingsUrl").value("/api/v1/settings"))
                .andExpect(jsonPath("$.data.accountActions.editProfileUrl").value("/api/v1/profile"))
                .andExpect(jsonPath("$.data.accountActions.logoutUrl").value("/auth/logout"));
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void getDashboard_missingProfile_returns404WithMessage() throws Exception {
        when(dashboardService.getDashboardData("test-user-id"))
                .thenThrow(new ResourceNotFoundException("Profile setup required"));

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Profile setup required"));
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void getDashboard_newUserWelcomeMessage() throws Exception {
        DashboardResponse newUserDashboard = new DashboardResponse(
                "Welcome to your new creator workspace, NewUser!",
                "2025-04-25T00:00:00Z",
                new ProfileSummary("NewUser", "GAMING", null, "2025-04-24T00:00:00Z", true, "INCOMPLETE"),
                sampleDashboard.navigationMenu(),
                sampleDashboard.revenuePlaceholder(),
                sampleDashboard.accountActions()
        );
        when(dashboardService.getDashboardData("test-user-id")).thenReturn(newUserDashboard);

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.welcomeMessage",
                        containsString("Welcome to your new creator workspace")))
                .andExpect(jsonPath("$.data.profileSummary.isNewUser").value(true))
                .andExpect(jsonPath("$.data.profileSummary.profileImageUrl").isEmpty());
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void getDashboard_responseIsLayoutNeutral_noEmbeddedMarkup() throws Exception {
        when(dashboardService.getDashboardData("test-user-id")).thenReturn(sampleDashboard);

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                // All top-level fields present and structured
                .andExpect(jsonPath("$.data.welcomeMessage", notNullValue()))
                .andExpect(jsonPath("$.data.currentDateTime", notNullValue()))
                .andExpect(jsonPath("$.data.profileSummary", notNullValue()))
                .andExpect(jsonPath("$.data.navigationMenu", notNullValue()))
                .andExpect(jsonPath("$.data.revenuePlaceholder", notNullValue()))
                .andExpect(jsonPath("$.data.accountActions", notNullValue()));
    }

    // --- GET /api/v1/settings ---

    @Test
    @WithMockUser(username = "test-user-id")
    void getSettings_authenticated_returns200StubResponse() throws Exception {
        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
