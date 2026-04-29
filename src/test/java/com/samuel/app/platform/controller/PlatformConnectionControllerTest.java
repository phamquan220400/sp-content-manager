package com.samuel.app.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.creator.repository.CreatorProfileRepository;
import com.samuel.app.platform.adapter.ConnectionStatus;
import com.samuel.app.platform.adapter.PlatformType;
import com.samuel.app.platform.dto.PlatformConnectionResponse;
import com.samuel.app.platform.dto.YouTubeAuthUrlResponse;
import com.samuel.app.platform.service.YouTubeConnectionService;
import com.samuel.app.shared.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
 * Unit tests for PlatformConnectionController.
 * Tests YouTube OAuth endpoints and connection management.
 */
@ExtendWith(MockitoExtension.class)
class PlatformConnectionControllerTest {
    
    @Mock
    private YouTubeConnectionService youTubeConnectionService;
    
    @Mock
    private CreatorProfileRepository creatorProfileRepository;
    
    @Mock
    private SecurityContext securityContext;
    
    @Mock
    private Authentication authentication;
    
    @InjectMocks
    private PlatformConnectionController platformConnectionController;
    
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
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
    void should_return_auth_url_when_authenticated_user_requests_youtube_auth() throws Exception {
        // Given
        String userId = "user-123";
        YouTubeAuthUrlResponse expectedResponse = new YouTubeAuthUrlResponse(
                "https://accounts.google.com/o/oauth2/v2/auth?client_id=test&state=abc123"
        );
        
        when(authentication.getName()).thenReturn(userId);
        when(youTubeConnectionService.getAuthorizationUrl(userId)).thenReturn(expectedResponse);
        
        // When & Then
        mockMvc.perform(get("/platforms/youtube/auth/url"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authorizationUrl").value(expectedResponse.authorizationUrl()));
        
        verify(youTubeConnectionService).getAuthorizationUrl(userId);
    }
    
    @Test
    void should_handle_callback_when_valid_code_and_state_provided() throws Exception {
        // Given
        String code = "auth-code-123";
        String state = "state-456";
        PlatformConnectionResponse expectedResponse = new PlatformConnectionResponse(
                PlatformType.YOUTUBE.name(),
                ConnectionStatus.CONNECTED.name(),
                "channel-789",
                "Test Channel",
                1000000L,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        
        when(youTubeConnectionService.handleCallback(code, state)).thenReturn(expectedResponse);
        
        // When & Then
        mockMvc.perform(get("/platforms/youtube/callback")
                        .param("code", code)
                        .param("state", state))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platformType").value(PlatformType.YOUTUBE.name()))
                .andExpect(jsonPath("$.data.status").value(ConnectionStatus.CONNECTED.name()));
        
        verify(youTubeConnectionService).handleCallback(code, state);
    }
    
    @Test
    void should_return_connection_status_when_authenticated_user_requests_status() throws Exception {
        // Given
        String userId = "user-123";
        String creatorProfileId = "creator-456";
        
        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId(creatorProfileId);
        
        PlatformConnectionResponse expectedResponse = new PlatformConnectionResponse(
                PlatformType.YOUTUBE.name(),
                ConnectionStatus.CONNECTED.name(),
                "channel-789",
                "My Channel",
                500000L,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        
        when(authentication.getName()).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(youTubeConnectionService.getConnectionStatus(creatorProfileId)).thenReturn(expectedResponse);
        
        // When & Then
        mockMvc.perform(get("/platforms/youtube/connection"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platformType").value(PlatformType.YOUTUBE.name()))
                .andExpect(jsonPath("$.data.status").value(ConnectionStatus.CONNECTED.name()))
                .andExpect(jsonPath("$.data.platformUserId").value("channel-789"));
        
        verify(youTubeConnectionService).getConnectionStatus(creatorProfileId);
    }
    
    @Test
    void should_return_disconnected_when_authenticated_user_disconnects() throws Exception {
        // Given
        String userId = "user-123";
        String creatorProfileId = "creator-456";
        
        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId(creatorProfileId);
        
        PlatformConnectionResponse expectedResponse = new PlatformConnectionResponse(
                PlatformType.YOUTUBE.name(),
                ConnectionStatus.DISCONNECTED.name(),
                null,
                null,
                null,
                null,
                LocalDateTime.now()
        );
        
        when(authentication.getName()).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(youTubeConnectionService.disconnectYouTube(creatorProfileId)).thenReturn(expectedResponse);
        
        // When & Then
        mockMvc.perform(delete("/platforms/youtube/disconnect"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value(ConnectionStatus.DISCONNECTED.name()));
        
        verify(youTubeConnectionService).disconnectYouTube(creatorProfileId);
    }
    
    @Test
    void should_handle_missing_code_parameter_in_callback() throws Exception {
        // When & Then
        mockMvc.perform(get("/platforms/youtube/callback")
                        .param("state", "test-state"))
                .andExpect(status().isBadRequest());
        
        // Verify service not called
        verify(youTubeConnectionService, never()).handleCallback(anyString(), anyString());
    }
    
    @Test
    void should_handle_missing_state_parameter_in_callback() throws Exception {
        // When & Then
        mockMvc.perform(get("/platforms/youtube/callback")
                        .param("code", "test-code"))
                .andExpect(status().isBadRequest());
        
        // Verify service not called
        verify(youTubeConnectionService, never()).handleCallback(anyString(), anyString());
    }
    
    @Test
    void should_propagate_service_exceptions_to_global_handler() throws Exception {
        // Given
        String userId = "user-123";
        String creatorProfileId = "creator-456";
        
        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId(creatorProfileId);
        
        when(authentication.getName()).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(youTubeConnectionService.getConnectionStatus(creatorProfileId))
                .thenThrow(new RuntimeException("Service error"));
        
        // When & Then
        mockMvc.perform(get("/platforms/youtube/connection"))
                .andExpect(status().isInternalServerError());
        
        verify(youTubeConnectionService).getConnectionStatus(creatorProfileId);
    }
}