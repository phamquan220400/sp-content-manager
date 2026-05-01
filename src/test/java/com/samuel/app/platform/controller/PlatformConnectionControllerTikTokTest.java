package com.samuel.app.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.creator.repository.CreatorProfileRepository;
import com.samuel.app.platform.adapter.ConnectionStatus;
import com.samuel.app.platform.adapter.PlatformType;
import com.samuel.app.platform.dto.PlatformConnectionResponse;
import com.samuel.app.platform.dto.TikTokAuthUrlResponse;
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
 * Unit tests for PlatformConnectionController — TikTok endpoints.
 * Uses standaloneSetup to avoid loading full Spring context.
 */
@ExtendWith(MockitoExtension.class)
class PlatformConnectionControllerTikTokTest {

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

    @Test
    void should_return_tiktok_auth_url_when_authenticated_user_requests() throws Exception {
        // Given
        String userId = "user-123";
        TikTokAuthUrlResponse expectedResponse = new TikTokAuthUrlResponse(
                "https://www.tiktok.com/v2/auth/authorize/?client_key=test-key&state=abc123"
        );

        when(authentication.getName()).thenReturn(userId);
        when(tikTokConnectionService.getAuthorizationUrl(userId)).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(get("/platforms/tiktok/auth/url"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authorizationUrl").value(expectedResponse.authorizationUrl()));

        verify(tikTokConnectionService).getAuthorizationUrl(userId);
    }

    @Test
    void should_return_connection_status_when_user_requests_tiktok_status() throws Exception {
        // Given
        String userId = "user-123";
        String creatorProfileId = "creator-456";

        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId(creatorProfileId);

        PlatformConnectionResponse expectedResponse = new PlatformConnectionResponse(
                PlatformType.TIKTOK.name(),
                ConnectionStatus.CONNECTED.name(),
                "open-id-abc",
                "Creator Name",
                50000L,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(authentication.getName()).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(tikTokConnectionService.getConnectionStatus(creatorProfileId)).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(get("/platforms/tiktok/connection"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platformType").value(PlatformType.TIKTOK.name()))
                .andExpect(jsonPath("$.data.status").value(ConnectionStatus.CONNECTED.name()))
                .andExpect(jsonPath("$.data.platformUserId").value("open-id-abc"));

        verify(tikTokConnectionService).getConnectionStatus(creatorProfileId);
    }

    @Test
    void should_return_success_when_user_disconnects_tiktok() throws Exception {
        // Given
        String userId = "user-123";
        String creatorProfileId = "creator-456";

        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId(creatorProfileId);

        PlatformConnectionResponse expectedResponse = new PlatformConnectionResponse(
                PlatformType.TIKTOK.name(),
                ConnectionStatus.DISCONNECTED.name(),
                null,
                null,
                null,
                null,
                LocalDateTime.now()
        );

        when(authentication.getName()).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(tikTokConnectionService.disconnectTikTok(creatorProfileId)).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(delete("/platforms/tiktok/disconnect"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value(ConnectionStatus.DISCONNECTED.name()));

        verify(tikTokConnectionService).disconnectTikTok(creatorProfileId);
    }

    @Test
    void should_return_bad_request_when_tiktok_sends_error_callback() throws Exception {
        // When & Then (TikTok sends ?error=access_denied&error_description=User+denied+authorization)
        mockMvc.perform(get("/platforms/tiktok/callback")
                        .param("error", "access_denied")
                        .param("error_description", "User denied authorization"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User denied authorization"));

        verifyNoInteractions(tikTokConnectionService);
    }
}
