package com.samuel.app.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.creator.repository.CreatorProfileRepository;
import com.samuel.app.platform.adapter.ConnectionStatus;
import com.samuel.app.platform.adapter.PlatformType;
import com.samuel.app.platform.dto.FacebookAuthUrlResponse;
import com.samuel.app.platform.dto.PlatformConnectionResponse;
import com.samuel.app.platform.service.FacebookConnectionService;
import com.samuel.app.platform.service.InstagramConnectionService;
import com.samuel.app.platform.service.TikTokConnectionService;
import com.samuel.app.platform.service.YouTubeConnectionService;
import com.samuel.app.shared.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for PlatformConnectionController — Facebook endpoints.
 * Uses standaloneSetup to avoid loading full Spring context.
 * Follows PlatformConnectionControllerInstagramTest pattern.
 */
@ExtendWith(MockitoExtension.class)
class PlatformConnectionControllerFacebookTest {

    @Mock
    private YouTubeConnectionService youTubeConnectionService;

    @Mock
    private TikTokConnectionService tikTokConnectionService;

    @Mock
    private InstagramConnectionService instagramConnectionService;

    @Mock
    private FacebookConnectionService facebookConnectionService;

    @Mock
    private CreatorProfileRepository creatorProfileRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private PlatformConnectionController platformConnectionController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        platformConnectionController = new PlatformConnectionController(
                youTubeConnectionService,
                tikTokConnectionService,
                instagramConnectionService,
                facebookConnectionService,
                creatorProfileRepository
        );
        mockMvc = MockMvcBuilders.standaloneSetup(platformConnectionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ────────────────────────────────────────────────────────────
    // GET /platforms/facebook/auth/url
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_facebook_auth_url_when_authenticated_user_requests() throws Exception {
        // Given
        String userId = "user-123";
        FacebookAuthUrlResponse expectedResponse = new FacebookAuthUrlResponse(
                "https://www.facebook.com/v18.0/dialog/oauth?client_id=test-id&state=abc123"
        );

        when(authentication.getName()).thenReturn(userId);
        when(facebookConnectionService.getAuthorizationUrl(userId)).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(get("/platforms/facebook/auth/url"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authorizationUrl").value(expectedResponse.authorizationUrl()));

        verify(facebookConnectionService).getAuthorizationUrl(userId);
    }

    // ────────────────────────────────────────────────────────────
    // GET /platforms/facebook/callback
    // ────────────────────────────────────────────────────────────

    @Test
    void should_handle_facebook_callback_and_return_connection_response() throws Exception {
        // Given
        String code = "auth-code-123";
        String state = "state-456";
        PlatformConnectionResponse expectedResponse = new PlatformConnectionResponse(
                PlatformType.FACEBOOK.name(),
                ConnectionStatus.CONNECTED.name(),
                "page-123",
                "Test Page",
                50000L,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(facebookConnectionService.handleCallback(code, state)).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(get("/platforms/facebook/callback")
                        .param("code", code)
                        .param("state", state))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platformType").value("FACEBOOK"))
                .andExpect(jsonPath("$.data.status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.platformUserId").value("page-123"))
                .andExpect(jsonPath("$.data.platformName").value("Test Page"))
                .andExpect(jsonPath("$.data.followerCount").value(50000));

        verify(facebookConnectionService).handleCallback(code, state);
    }

    @Test
    void should_return_bad_request_when_facebook_callback_has_error_param() throws Exception {
        // When & Then — Meta redirects with error param, no code/state
        mockMvc.perform(get("/platforms/facebook/callback")
                        .param("error", "access_denied")
                        .param("error_description", "The user denied access"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("The user denied access"));

        verifyNoInteractions(facebookConnectionService);
    }

    // ────────────────────────────────────────────────────────────
    // GET /platforms/facebook/connection
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_connection_status_when_user_requests_facebook_status() throws Exception {
        // Given
        String userId = "user-789";
        String creatorProfileId = "creator-profile-123";

        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId(creatorProfileId);
        creatorProfile.setUserId(userId);

        PlatformConnectionResponse expectedResponse = new PlatformConnectionResponse(
                PlatformType.FACEBOOK.name(),
                ConnectionStatus.CONNECTED.name(),
                "page-456",
                "Creator Page",
                75000L,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(authentication.getName()).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(facebookConnectionService.getConnectionStatus(creatorProfileId)).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(get("/platforms/facebook/connection"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platformType").value("FACEBOOK"))
                .andExpect(jsonPath("$.data.status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.platformUserId").value("page-456"))
                .andExpect(jsonPath("$.data.platformName").value("Creator Page"))
                .andExpect(jsonPath("$.data.followerCount").value(75000));

        verify(facebookConnectionService).getConnectionStatus(creatorProfileId);
        verify(creatorProfileRepository).findByUserId(userId);
    }

    // ────────────────────────────────────────────────────────────
    // DELETE /platforms/facebook/disconnect
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_success_when_user_disconnects_facebook() throws Exception {
        // Given
        String userId = "user-disconnect";
        String creatorProfileId = "creator-disconnect";

        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId(creatorProfileId);
        creatorProfile.setUserId(userId);

        PlatformConnectionResponse expectedResponse = new PlatformConnectionResponse(
                PlatformType.FACEBOOK.name(),
                ConnectionStatus.DISCONNECTED.name(),
                "page-disconnected",
                "Disconnected Page",
                null,
                null,
                LocalDateTime.now()
        );

        when(authentication.getName()).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(facebookConnectionService.disconnectFacebook(creatorProfileId)).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(delete("/platforms/facebook/disconnect"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platformType").value("FACEBOOK"))
                .andExpect(jsonPath("$.data.status").value("DISCONNECTED"))
                .andExpect(jsonPath("$.data.platformUserId").value("page-disconnected"))
                .andExpect(jsonPath("$.data.platformName").value("Disconnected Page"));

        verify(facebookConnectionService).disconnectFacebook(creatorProfileId);
        verify(creatorProfileRepository).findByUserId(userId);
    }
}