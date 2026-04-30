package com.samuel.app.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.creator.repository.CreatorProfileRepository;
import com.samuel.app.platform.adapter.ConnectionStatus;
import com.samuel.app.platform.adapter.PlatformType;
import com.samuel.app.platform.dto.InstagramAuthUrlResponse;
import com.samuel.app.platform.dto.PlatformConnectionResponse;
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
 * Unit tests for PlatformConnectionController — Instagram endpoints.
 * Uses standaloneSetup to avoid loading full Spring context.
 * Follows PlatformConnectionControllerTikTokTest pattern.
 */
@ExtendWith(MockitoExtension.class)
class PlatformConnectionControllerInstagramTest {

    @Mock
    private YouTubeConnectionService youTubeConnectionService;

    @Mock
    private TikTokConnectionService tikTokConnectionService;

    @Mock
    private InstagramConnectionService instagramConnectionService;

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
    // GET /platforms/instagram/auth/url
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_instagram_auth_url_when_authenticated_user_requests() throws Exception {
        // Given
        String userId = "user-123";
        InstagramAuthUrlResponse expectedResponse = new InstagramAuthUrlResponse(
                "https://www.facebook.com/v18.0/dialog/oauth?client_id=test-id&state=abc123"
        );

        when(authentication.getName()).thenReturn(userId);
        when(instagramConnectionService.getAuthorizationUrl(userId)).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(get("/platforms/instagram/auth/url"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authorizationUrl").value(expectedResponse.authorizationUrl()));

        verify(instagramConnectionService).getAuthorizationUrl(userId);
    }

    // ────────────────────────────────────────────────────────────
    // GET /platforms/instagram/callback
    // ────────────────────────────────────────────────────────────

    @Test
    void should_handle_instagram_callback_successfully() throws Exception {
        // Given
        String code = "auth-code-123";
        String state = "state-456";
        PlatformConnectionResponse expectedResponse = new PlatformConnectionResponse(
                PlatformType.INSTAGRAM.name(),
                ConnectionStatus.CONNECTED.name(),
                "ig-user-123",
                "testuser",
                10000L,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(instagramConnectionService.handleCallback(code, state)).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(get("/platforms/instagram/callback")
                        .param("code", code)
                        .param("state", state))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platformType").value("INSTAGRAM"))
                .andExpect(jsonPath("$.data.status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.platformUserId").value("ig-user-123"))
                .andExpect(jsonPath("$.data.platformName").value("testuser"))
                .andExpect(jsonPath("$.data.followerCount").value(10000));

        verify(instagramConnectionService).handleCallback(code, state);
    }

    // ────────────────────────────────────────────────────────────
    // GET /platforms/instagram/connection
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_connection_status_when_user_requests_instagram_status() throws Exception {
        // Given
        String userId = "user-789";
        String creatorProfileId = "creator-profile-123";

        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId(creatorProfileId);
        creatorProfile.setUserId(userId);

        PlatformConnectionResponse expectedResponse = new PlatformConnectionResponse(
                PlatformType.INSTAGRAM.name(),
                ConnectionStatus.CONNECTED.name(),
                "ig-user-456",
                "creator_account",
                25000L,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(authentication.getName()).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(instagramConnectionService.getConnectionStatus(creatorProfileId)).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(get("/platforms/instagram/connection"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platformType").value("INSTAGRAM"))
                .andExpect(jsonPath("$.data.status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.platformUserId").value("ig-user-456"))
                .andExpect(jsonPath("$.data.platformName").value("creator_account"))
                .andExpect(jsonPath("$.data.followerCount").value(25000));

        verify(instagramConnectionService).getConnectionStatus(creatorProfileId);
        verify(creatorProfileRepository).findByUserId(userId);
    }

    @Test
    void should_return_disconnected_status_when_no_instagram_connection() throws Exception {
        // Given
        String userId = "user-999";
        String creatorProfileId = "creator-profile-999";

        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId(creatorProfileId);
        creatorProfile.setUserId(userId);

        PlatformConnectionResponse expectedResponse = new PlatformConnectionResponse(
                PlatformType.INSTAGRAM.name(),
                ConnectionStatus.DISCONNECTED.name(),
                null,
                null,
                null,
                null,
                null
        );

        when(authentication.getName()).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(instagramConnectionService.getConnectionStatus(creatorProfileId)).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(get("/platforms/instagram/connection"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platformType").value("INSTAGRAM"))
                .andExpect(jsonPath("$.data.status").value("DISCONNECTED"))
                .andExpect(jsonPath("$.data.platformUserId").isEmpty())
                .andExpect(jsonPath("$.data.platformName").isEmpty())
                .andExpect(jsonPath("$.data.followerCount").isEmpty());

        verify(instagramConnectionService).getConnectionStatus(creatorProfileId);
    }

    // ────────────────────────────────────────────────────────────
    // DELETE /platforms/instagram/disconnect
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_success_when_user_disconnects_instagram() throws Exception {
        // Given
        String userId = "user-disconnect";
        String creatorProfileId = "creator-disconnect";

        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId(creatorProfileId);
        creatorProfile.setUserId(userId);

        PlatformConnectionResponse expectedResponse = new PlatformConnectionResponse(
                PlatformType.INSTAGRAM.name(),
                ConnectionStatus.DISCONNECTED.name(),
                "ig-user-disconnected",
                "disconnected_account",
                null,
                null,
                LocalDateTime.now()
        );

        when(authentication.getName()).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(instagramConnectionService.disconnectInstagram(creatorProfileId)).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(delete("/platforms/instagram/disconnect"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platformType").value("INSTAGRAM"))
                .andExpect(jsonPath("$.data.status").value("DISCONNECTED"))
                .andExpect(jsonPath("$.data.platformUserId").value("ig-user-disconnected"))
                .andExpect(jsonPath("$.data.platformName").value("disconnected_account"));

        verify(instagramConnectionService).disconnectInstagram(creatorProfileId);
        verify(creatorProfileRepository).findByUserId(userId);
    }
}