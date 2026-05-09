package com.samuel.app.platform.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.samuel.app.exceptions.ResourceNotFoundException;
import com.samuel.app.platform.adapter.ConnectionStatus;
import com.samuel.app.platform.adapter.PlatformType;
import com.samuel.app.platform.config.PlatformEndpointResolver;
import com.samuel.app.platform.config.YouTubeProperties;
import com.samuel.app.platform.dto.YouTubeTokenResponse;
import com.samuel.app.platform.dto.YouTubeVideoUploadRequest;
import com.samuel.app.platform.dto.YouTubeVideoUploadResponse;
import com.samuel.app.platform.exception.PlatformConnectionException;
import com.samuel.app.platform.model.PlatformConnection;
import com.samuel.app.platform.repository.PlatformConnectionRepository;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for uploading videos to YouTube via the YouTube Data API v3.
 * Uses the resumable upload protocol for reliable large-file uploads.
 * Requires the creator's YouTube connection to have the youtube.upload OAuth scope.
 */
@Service
public class YouTubeVideoUploadService {

    private static final String DEFAULT_CATEGORY_ID = "22"; // People & Blogs

    private final PlatformConnectionRepository platformConnectionRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final YouTubeProperties youtubeProperties;
    private final PlatformEndpointResolver platformEndpoints;
    private final RestTemplate restTemplate;

    public YouTubeVideoUploadService(
            PlatformConnectionRepository platformConnectionRepository,
            TokenEncryptionService tokenEncryptionService,
            YouTubeProperties youtubeProperties,
            PlatformEndpointResolver platformEndpoints,
            RestTemplate restTemplate) {
        this.platformConnectionRepository = platformConnectionRepository;
        this.tokenEncryptionService = tokenEncryptionService;
        this.youtubeProperties = youtubeProperties;
        this.platformEndpoints = platformEndpoints;
        this.restTemplate = restTemplate;
    }

    /**
     * Uploads a video file to YouTube using the connected creator's OAuth credentials.
     * Uses the YouTube Data API v3 resumable upload protocol:
     * Step 1 — POST metadata to obtain a session upload URI.
     * Step 2 — PUT the video bytes to that URI to complete the upload.
     *
     * @param creatorProfileId the creator profile whose YouTube connection is used
     * @param uploadRequest    video metadata (title, description, category, privacy, tags)
     * @param videoFile        the video file to upload
     * @return upload response containing the new video ID and public URL
     */
    public YouTubeVideoUploadResponse uploadVideo(
            String creatorProfileId,
            YouTubeVideoUploadRequest uploadRequest,
            MultipartFile videoFile) {

        PlatformConnection connection = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.YOUTUBE)
                .orElseThrow(() -> new ResourceNotFoundException("YouTube connection not found"));

        if (connection.getStatus() != ConnectionStatus.CONNECTED
                || connection.getAccessTokenEncrypted() == null) {
            throw new PlatformConnectionException(PlatformType.YOUTUBE,
                    "YouTube account is not connected. Please connect your YouTube channel first.");
        }

        String accessToken = resolveAccessToken(connection);
        String contentType = resolveVideoContentType(videoFile);

        URI uploadUri = initiateResumableUpload(accessToken, uploadRequest, contentType, videoFile.getSize());
        return uploadVideoContent(uploadUri, videoFile, contentType);
    }

    /**
     * Returns a valid (non-expired) access token, refreshing it when necessary.
     * Persists refreshed tokens back to the database.
     */
    private String resolveAccessToken(PlatformConnection connection) {
        LocalDateTime expiresAt = connection.getTokenExpiresAt();
        boolean isExpired = expiresAt == null || LocalDateTime.now().isAfter(expiresAt.minusMinutes(1));

        if (!isExpired) {
            return tokenEncryptionService.decrypt(connection.getAccessTokenEncrypted());
        }

        if (connection.getRefreshTokenEncrypted() == null) {
            throw new PlatformConnectionException(PlatformType.YOUTUBE,
                    "YouTube access token has expired and no refresh token is available. Please reconnect.");
        }

        String refreshToken = tokenEncryptionService.decrypt(connection.getRefreshTokenEncrypted());
        YouTubeTokenResponse refreshed = refreshAccessToken(refreshToken);

        connection.setAccessTokenEncrypted(tokenEncryptionService.encrypt(refreshed.accessToken()));
        connection.setTokenExpiresAt(LocalDateTime.now().plusSeconds(refreshed.expiresIn()));
        platformConnectionRepository.save(connection);

        return refreshed.accessToken();
    }

    /**
     * Exchanges a refresh token for a new access token via the Google OAuth2 token endpoint.
     */
    private YouTubeTokenResponse refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", youtubeProperties.getClientId());
        params.add("client_secret", youtubeProperties.getClientSecret());
        params.add("refresh_token", refreshToken);
        params.add("grant_type", "refresh_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            YouTubeTokenResponse response = restTemplate.postForObject(platformEndpoints.getTokenUrl(PlatformType.YOUTUBE), request,
                    YouTubeTokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new PlatformConnectionException(PlatformType.YOUTUBE,
                        "Failed to refresh YouTube access token: empty response");
            }
            return response;
        } catch (PlatformConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.YOUTUBE,
                    "Failed to refresh YouTube access token", e);
        }
    }

    /**
     * Step 1 of the resumable upload: POST metadata to YouTube to obtain a session upload URI.
     */
    private URI initiateResumableUpload(String accessToken, YouTubeVideoUploadRequest uploadRequest,
                                        String contentType, long fileSize) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Upload-Content-Type", contentType);
        headers.set("X-Upload-Content-Length", String.valueOf(fileSize));

        Map<String, Object> snippet = new HashMap<>();
        snippet.put("title", uploadRequest.title());
        snippet.put("description", uploadRequest.description() != null ? uploadRequest.description() : "");
        snippet.put("categoryId", uploadRequest.categoryId() != null
                ? uploadRequest.categoryId() : DEFAULT_CATEGORY_ID);
        if (uploadRequest.tags() != null && !uploadRequest.tags().isEmpty()) {
            snippet.put("tags", uploadRequest.tags());
        }

        Map<String, Object> status = new HashMap<>();
        status.put("privacyStatus", uploadRequest.privacyStatus() != null
                ? uploadRequest.privacyStatus() : "private");

        Map<String, Object> body = new HashMap<>();
        body.put("snippet", snippet);
        body.put("status", status);

        String url = platformEndpoints.getYouTube().getUploadUrl() + "?uploadType=resumable&part=snippet,status";
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.POST,
                    requestEntity, Void.class);
            URI location = response.getHeaders().getLocation();
            if (location == null) {
                throw new PlatformConnectionException(PlatformType.YOUTUBE,
                        "YouTube did not return an upload URI. Please try again.");
            }
            return location;
        } catch (PlatformConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.YOUTUBE,
                    "Failed to initiate YouTube video upload session", e);
        }
    }

    /**
     * Step 2 of the resumable upload: PUT the video bytes to the session upload URI.
     * Uses InputStreamResource to stream the file without loading it fully into heap.
     */
    private YouTubeVideoUploadResponse uploadVideoContent(URI uploadUri, MultipartFile videoFile,
                                                          String contentType) {
        try {
            InputStreamResource videoResource = new InputStreamResource(videoFile.getInputStream()) {
                @Override
                public long contentLength() {
                    return videoFile.getSize();
                }

                @Override
                public String getFilename() {
                    return videoFile.getOriginalFilename();
                }
            };

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(videoFile.getSize());
            HttpEntity<InputStreamResource> requestEntity = new HttpEntity<>(videoResource, headers);

            ResponseEntity<YouTubeVideoResource> response = restTemplate.exchange(
                    uploadUri, HttpMethod.PUT, requestEntity, YouTubeVideoResource.class);

            YouTubeVideoResource resource = response.getBody();
            if (resource == null || resource.id() == null) {
                throw new PlatformConnectionException(PlatformType.YOUTUBE,
                        "YouTube returned an invalid upload response.");
            }

            String privacyStatus = resource.status() != null ? resource.status().privacyStatus() : "unknown";
            String uploadStatus = resource.status() != null ? resource.status().uploadStatus() : "unknown";
            String title = resource.snippet() != null ? resource.snippet().title() : null;

            return new YouTubeVideoUploadResponse(
                    resource.id(),
                    title,
                    privacyStatus,
                    uploadStatus,
                    platformEndpoints.getYouTube().getVideoBaseUrl() + resource.id()
            );
        } catch (IOException e) {
            throw new PlatformConnectionException(PlatformType.YOUTUBE,
                    "Failed to read video file for upload", e);
        } catch (PlatformConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.YOUTUBE,
                    "Failed to upload video content to YouTube", e);
        }
    }

    private String resolveVideoContentType(MultipartFile videoFile) {
        String ct = videoFile.getContentType();
        return (ct != null && ct.startsWith("video/")) ? ct : "video/mp4";
    }

    /**
     * Internal record mapping the YouTube Data API v3 videos.insert response.
     */
    private record YouTubeVideoResource(
            String id,
            @JsonProperty("snippet") VideoSnippet snippet,
            @JsonProperty("status") VideoStatus status
    ) {
        private record VideoSnippet(String title) {
        }

        private record VideoStatus(
                @JsonProperty("privacyStatus") String privacyStatus,
                @JsonProperty("uploadStatus") String uploadStatus
        ) {
        }
    }
}
