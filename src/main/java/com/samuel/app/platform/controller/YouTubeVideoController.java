package com.samuel.app.platform.controller;

import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.creator.repository.CreatorProfileRepository;
import com.samuel.app.exceptions.ResourceNotFoundException;
import com.samuel.app.platform.dto.YouTubeVideoUploadRequest;
import com.samuel.app.platform.dto.YouTubeVideoUploadResponse;
import com.samuel.app.platform.service.YouTubeVideoUploadService;
import com.samuel.app.shared.controller.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for YouTube video management.
 * Exposes video upload to YouTube via the connected creator's OAuth credentials.
 */
@RestController
@RequestMapping("/platforms/youtube/videos")
public class YouTubeVideoController {

    private final YouTubeVideoUploadService youTubeVideoUploadService;
    private final CreatorProfileRepository creatorProfileRepository;

    public YouTubeVideoController(
            YouTubeVideoUploadService youTubeVideoUploadService,
            CreatorProfileRepository creatorProfileRepository) {
        this.youTubeVideoUploadService = youTubeVideoUploadService;
        this.creatorProfileRepository = creatorProfileRepository;
    }

    /**
     * Uploads a video to YouTube using the authenticated creator's connected YouTube account.
     * The creator must have completed the YouTube OAuth flow (youtube.upload scope required).
     *
     * <p>POST /platforms/youtube/videos/upload (multipart/form-data)</p>
     *
     * @param file          the video file (required)
     * @param title         video title shown on YouTube (required)
     * @param description   optional video description
     * @param categoryId    YouTube category ID (default: 22 — People &amp; Blogs)
     * @param privacyStatus visibility: public, unlisted, or private (default: private)
     * @param tags          optional list of tags
     * @return the uploaded video ID, title, status, and YouTube URL
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<YouTubeVideoUploadResponse>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "categoryId", defaultValue = "22") String categoryId,
            @RequestParam(value = "privacyStatus", defaultValue = "private") String privacyStatus,
            @RequestParam(value = "tags", required = false) List<String> tags) {

        String creatorProfileId = getCreatorProfileId();
        YouTubeVideoUploadRequest request = new YouTubeVideoUploadRequest(
                title, description, categoryId, privacyStatus, tags);
        YouTubeVideoUploadResponse response = youTubeVideoUploadService.uploadVideo(
                creatorProfileId, request, file);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private String getCreatorProfileId() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        CreatorProfile creatorProfile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user: " + userId));
        return creatorProfile.getId();
    }
}
