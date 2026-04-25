package com.samuel.app.creator.controller;

import com.samuel.app.creator.dto.CreateProfileRequest;
import com.samuel.app.creator.dto.CreatorProfileResponse;
import com.samuel.app.creator.dto.ProfileImageResponse;
import com.samuel.app.creator.dto.UpdateProfileRequest;
import com.samuel.app.creator.service.CreatorProfileService;
import com.samuel.app.shared.controller.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/profile")
public class CreatorProfileController {
    
    private final CreatorProfileService creatorProfileService;
    
    public CreatorProfileController(CreatorProfileService creatorProfileService) {
        this.creatorProfileService = creatorProfileService;
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<CreatorProfileResponse>> createProfile(@Valid @RequestBody CreateProfileRequest request) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        CreatorProfileResponse response = creatorProfileService.createProfile(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response));
    }
    
    @GetMapping
    public ResponseEntity<ApiResponse<CreatorProfileResponse>> getProfile() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        CreatorProfileResponse response = creatorProfileService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
    
    @PutMapping
    public ResponseEntity<ApiResponse<CreatorProfileResponse>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        CreatorProfileResponse response = creatorProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
    
    @PostMapping("/image")
    public ResponseEntity<ApiResponse<ProfileImageResponse>> uploadImage(@RequestParam MultipartFile file) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        ProfileImageResponse response = creatorProfileService.saveProfileImage(userId, file);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}