package com.samuel.app.creator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.app.creator.dto.CreateProfileRequest;
import com.samuel.app.creator.dto.CreatorProfileResponse;
import com.samuel.app.creator.dto.ProfileImageResponse;
import com.samuel.app.creator.dto.UpdateProfileRequest;
import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.creator.repository.CreatorProfileRepository;
import com.samuel.app.exceptions.ProfileAlreadyExistsException;
import com.samuel.app.exceptions.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CreatorProfileService {
    
    private final CreatorProfileRepository creatorProfileRepository;
    private final ObjectMapper objectMapper;
    
    public CreatorProfileService(CreatorProfileRepository creatorProfileRepository, ObjectMapper objectMapper) {
        this.creatorProfileRepository = creatorProfileRepository;
        this.objectMapper = objectMapper;
    }
    
    public CreatorProfileResponse createProfile(String userId, CreateProfileRequest request) {
        if (creatorProfileRepository.existsByUserId(userId)) {
            throw new ProfileAlreadyExistsException("Creator profile already exists.");
        }
        
        CreatorProfile profile = new CreatorProfile();
        profile.setId(UUID.randomUUID().toString());
        profile.setUserId(userId);
        profile.setDisplayName(request.displayName());
        profile.setBio(request.bio());
        profile.setCreatorCategory(request.creatorCategory());
        
        // Serialize JSON fields
        try {
            if (request.contentPreferences() != null && !request.contentPreferences().isEmpty()) {
                profile.setContentPreferences(objectMapper.writeValueAsString(request.contentPreferences()));
            }
            if (request.notificationSettings() != null && !request.notificationSettings().isEmpty()) {
                profile.setNotificationSettings(objectMapper.writeValueAsString(request.notificationSettings()));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON fields", e);
        }
        
        CreatorProfile savedProfile = creatorProfileRepository.save(profile);
        return toResponse(savedProfile);
    }
    
    public CreatorProfileResponse getProfile(String userId) {
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found."));
        return toResponse(profile);
    }
    
    @Transactional
    public CreatorProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found."));
        
        // Apply partial updates - only update non-null fields
        if (request.displayName() != null) {
            profile.setDisplayName(request.displayName());
        }
        if (request.bio() != null) {
            profile.setBio(request.bio());
        }
        if (request.creatorCategory() != null) {
            profile.setCreatorCategory(request.creatorCategory());
        }
        
        try {
            if (request.contentPreferences() != null) {
                profile.setContentPreferences(objectMapper.writeValueAsString(request.contentPreferences()));
            }
            if (request.notificationSettings() != null) {
                profile.setNotificationSettings(objectMapper.writeValueAsString(request.notificationSettings()));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON fields", e);
        }
        
        CreatorProfile savedProfile = creatorProfileRepository.save(profile);
        return toResponse(savedProfile);
    }
    
    public ProfileImageResponse saveProfileImage(String userId, MultipartFile file) {
        // Validate MIME type
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png") && !contentType.equals("image/webp"))) {
            throw new IllegalArgumentException("Invalid image type. Allowed: JPEG, PNG, WEBP.");
        }
        
        // Validate size (2 MB = 2 * 1024 * 1024 bytes)
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("Image size must not exceed 2 MB.");
        }
        
        // Determine extension from content type
        String ext = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Invalid image type. Allowed: JPEG, PNG, WEBP.");
        };
        
        // Verify profile exists before touching the filesystem
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found."));
        
        try {
            // Build path and create directories
            String filename = UUID.randomUUID() + "." + ext;
            Path uploadDir = Paths.get("uploads", "profile-images", userId);
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(filename);
            
            // Write file
            Files.write(target, file.getBytes());
            
            // Build relative path and update profile
            String relativePath = "uploads/profile-images/" + userId + "/" + filename;
            profile.setProfileImageUrl(relativePath);
            creatorProfileRepository.save(profile);
            
            return new ProfileImageResponse(relativePath);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to save profile image", e);
        }
    }
    
    private CreatorProfileResponse toResponse(CreatorProfile profile) {
        List<String> contentPreferences = null;
        Map<String, Object> notificationSettings = null;
        
        try {
            if (profile.getContentPreferences() != null) {
                contentPreferences = objectMapper.readValue(
                    profile.getContentPreferences(), 
                    new TypeReference<List<String>>() {}
                );
            }
            if (profile.getNotificationSettings() != null) {
                notificationSettings = objectMapper.readValue(
                    profile.getNotificationSettings(), 
                    new TypeReference<Map<String, Object>>() {}
                );
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON fields", e);
        }
        
        return new CreatorProfileResponse(
            profile.getId(),
            profile.getUserId(),
            profile.getDisplayName(),
            profile.getBio(),
            profile.getCreatorCategory(),
            contentPreferences,
            notificationSettings,
            profile.getProfileImageUrl(),
            profile.getCreatedAt(),
            profile.getUpdatedAt()
        );
    }
}