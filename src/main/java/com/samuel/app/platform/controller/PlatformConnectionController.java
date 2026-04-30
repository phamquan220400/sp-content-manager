package com.samuel.app.platform.controller;

import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.creator.repository.CreatorProfileRepository;
import com.samuel.app.exceptions.ResourceNotFoundException;
import com.samuel.app.platform.dto.InstagramAuthUrlResponse;
import com.samuel.app.platform.dto.PlatformConnectionResponse;
import com.samuel.app.platform.dto.TikTokAuthUrlResponse;
import com.samuel.app.platform.dto.YouTubeAuthUrlResponse;
import com.samuel.app.platform.service.InstagramConnectionService;
import com.samuel.app.platform.service.TikTokConnectionService;
import com.samuel.app.platform.service.YouTubeConnectionService;
import com.samuel.app.shared.controller.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * REST controller for platform connection management.
 * Handles YouTube, TikTok, and Instagram OAuth flows and connection status operations.
 */
@RestController
@RequestMapping("/platforms")
public class PlatformConnectionController {

    private final YouTubeConnectionService youTubeConnectionService;
    private final TikTokConnectionService tikTokConnectionService;
    private final InstagramConnectionService instagramConnectionService;
    private final CreatorProfileRepository creatorProfileRepository;

    public PlatformConnectionController(
            YouTubeConnectionService youTubeConnectionService,
            TikTokConnectionService tikTokConnectionService,
            InstagramConnectionService instagramConnectionService,
            CreatorProfileRepository creatorProfileRepository) {
        this.youTubeConnectionService = youTubeConnectionService;
        this.tikTokConnectionService = tikTokConnectionService;
        this.instagramConnectionService = instagramConnectionService;
        this.creatorProfileRepository = creatorProfileRepository;
    }

    /**
     * Generates YouTube OAuth authorization URL for authenticated user.
     *
     * @return response containing Google OAuth authorization URL
     */
    @GetMapping("/youtube/auth/url")
    public ResponseEntity<ApiResponse<YouTubeAuthUrlResponse>> getYouTubeAuthUrl() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        YouTubeAuthUrlResponse response = youTubeConnectionService.getAuthorizationUrl(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Handles OAuth callback from Google authorization.
     * This endpoint is NOT authenticated (permitAll in SecurityConfig).
     *
     * @param code             authorization code from Google (absent on error callback)
     * @param state            CSRF state token (absent on error callback)
     * @param error            OAuth error code from Google (present on error callback)
     * @param errorDescription human-readable error detail from Google
     * @return platform connection response with connection details
     */
    @GetMapping("/youtube/callback")
    public ResponseEntity<ApiResponse<PlatformConnectionResponse>> handleYouTubeCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription) {
        if (error != null || code == null || state == null) {
            String raw = errorDescription != null ? errorDescription : (error != null ? error : "Missing OAuth parameters");
            String message = raw.replaceAll("[\\r\\n\\t]", " ").strip();
            if (message.length() > 200) message = message.substring(0, 200);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, null, message, null, LocalDateTime.now()));
        }
        PlatformConnectionResponse response = youTubeConnectionService.handleCallback(code, state);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Gets YouTube connection status for authenticated user.
     *
     * @return platform connection response with status and details
     */
    @GetMapping("/youtube/connection")
    public ResponseEntity<ApiResponse<PlatformConnectionResponse>> getYouTubeConnectionStatus() {
        String creatorProfileId = getCreatorProfileId();
        PlatformConnectionResponse response = youTubeConnectionService.getConnectionStatus(creatorProfileId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Disconnects YouTube channel for authenticated user.
     *
     * @return updated platform connection response
     */
    @DeleteMapping("/youtube/disconnect")
    public ResponseEntity<ApiResponse<PlatformConnectionResponse>> disconnectYouTube() {
        String creatorProfileId = getCreatorProfileId();
        PlatformConnectionResponse response = youTubeConnectionService.disconnectYouTube(creatorProfileId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Generates TikTok OAuth authorization URL for authenticated user.
     *
     * @return response containing TikTok OAuth authorization URL
     */
    @GetMapping("/tiktok/auth/url")
    public ResponseEntity<ApiResponse<TikTokAuthUrlResponse>> getTikTokAuthUrl() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        TikTokAuthUrlResponse response = tikTokConnectionService.getAuthorizationUrl(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Handles OAuth callback from TikTok authorization.
     * This endpoint is NOT authenticated (permitAll in SecurityConfig).
     *
     * @param code             authorization code from TikTok (absent on error callback)
     * @param state            CSRF state token (absent on error callback)
     * @param error            OAuth error code from TikTok (present on error callback)
     * @param errorDescription human-readable error detail from TikTok
     * @return platform connection response with connection details
     */
    @GetMapping("/tiktok/callback")
    public ResponseEntity<ApiResponse<PlatformConnectionResponse>> handleTikTokCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription) {
        if (error != null || code == null || state == null) {
            String raw = errorDescription != null ? errorDescription : (error != null ? error : "Missing OAuth parameters");
            String message = raw.replaceAll("[\\r\\n\\t]", " ").strip();
            if (message.length() > 200) message = message.substring(0, 200);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, null, message, null, LocalDateTime.now()));
        }
        PlatformConnectionResponse response = tikTokConnectionService.handleCallback(code, state);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Gets TikTok connection status for authenticated user.
     *
     * @return platform connection response with status and details
     */
    @GetMapping("/tiktok/connection")
    public ResponseEntity<ApiResponse<PlatformConnectionResponse>> getTikTokConnectionStatus() {
        String creatorProfileId = getCreatorProfileId();
        PlatformConnectionResponse response = tikTokConnectionService.getConnectionStatus(creatorProfileId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Disconnects TikTok account for authenticated user.
     *
     * @return updated platform connection response
     */
    @DeleteMapping("/tiktok/disconnect")
    public ResponseEntity<ApiResponse<PlatformConnectionResponse>> disconnectTikTok() {
        String creatorProfileId = getCreatorProfileId();
        PlatformConnectionResponse response = tikTokConnectionService.disconnectTikTok(creatorProfileId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Generates Instagram OAuth authorization URL for authenticated user.
     *
     * @return response containing Instagram OAuth authorization URL
     */
    @GetMapping("/instagram/auth/url")
    public ResponseEntity<ApiResponse<InstagramAuthUrlResponse>> getInstagramAuthUrl() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        InstagramAuthUrlResponse response = instagramConnectionService.getAuthorizationUrl(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Handles OAuth callback from Instagram authorization.
     * This endpoint is NOT authenticated (permitAll in SecurityConfig).
     *
     * @param code             authorization code from Meta (absent on user denial)
     * @param state            CSRF state token (absent on user denial)
     * @param error            OAuth error code from Meta (present when user denies or error occurs)
     * @param errorDescription human-readable error detail from Meta
     * @return platform connection response with connection details
     */
    @GetMapping("/instagram/callback")
    public ResponseEntity<ApiResponse<PlatformConnectionResponse>> handleInstagramCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription) {
        if (error != null || code == null || state == null) {
            String raw = errorDescription != null ? errorDescription : (error != null ? error : "Missing OAuth parameters");
            String message = raw.replaceAll("[\\r\\n\\t]", " ").strip();
            if (message.length() > 200) message = message.substring(0, 200);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, null, message, null, LocalDateTime.now()));
        }
        PlatformConnectionResponse response = instagramConnectionService.handleCallback(code, state);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Gets Instagram connection status for authenticated user.
     *
     * @return platform connection response with status and details
     */
    @GetMapping("/instagram/connection")
    public ResponseEntity<ApiResponse<PlatformConnectionResponse>> getInstagramConnectionStatus() {
        String creatorProfileId = getCreatorProfileId();
        PlatformConnectionResponse response = instagramConnectionService.getConnectionStatus(creatorProfileId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Disconnects Instagram account for authenticated user.
     *
     * @return updated platform connection response
     */
    @DeleteMapping("/instagram/disconnect")
    public ResponseEntity<ApiResponse<PlatformConnectionResponse>> disconnectInstagram() {
        String creatorProfileId = getCreatorProfileId();
        PlatformConnectionResponse response = instagramConnectionService.disconnectInstagram(creatorProfileId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Helper method to get creator profile ID from authenticated user.
     * Follows established pattern from CreatorProfileController.
     */
    private String getCreatorProfileId() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        CreatorProfile creatorProfile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user: " + userId));
        return creatorProfile.getId();
    }
}