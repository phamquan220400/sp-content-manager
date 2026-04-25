package com.samuel.app.creator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.app.creator.dto.CreateProfileRequest;
import com.samuel.app.creator.dto.CreatorProfileResponse;
import com.samuel.app.creator.dto.ProfileImageResponse;
import com.samuel.app.creator.dto.UpdateProfileRequest;
import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.creator.model.CreatorProfile.CreatorCategory;
import com.samuel.app.creator.repository.CreatorProfileRepository;
import com.samuel.app.exceptions.ProfileAlreadyExistsException;
import com.samuel.app.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.MockedStatic;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorProfileServiceTest {

    @Mock
    private CreatorProfileRepository creatorProfileRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CreatorProfileService creatorProfileService;

    @Test
    void createProfile_newUser_createsAndReturnsProfile() throws JsonProcessingException {
        // Given
        String userId = "test-user-id";
        List<String> contentPrefs = List.of("gaming", "tech");
        Map<String, Object> notifications = Map.of("email", true, "push", false);
        CreateProfileRequest request = new CreateProfileRequest(
            "TestCreator",
            "Test bio",
            CreatorCategory.GAMING,
            contentPrefs,
            notifications
        );

        CreatorProfile savedProfile = new CreatorProfile();
        savedProfile.setId("profile-id");
        savedProfile.setUserId(userId);
        savedProfile.setDisplayName("TestCreator");
        savedProfile.setBio("Test bio");
        savedProfile.setCreatorCategory(CreatorCategory.GAMING);
        savedProfile.setContentPreferences("[\"gaming\",\"tech\"]");
        savedProfile.setNotificationSettings("{\"email\":true,\"push\":false}");

        when(creatorProfileRepository.existsByUserId(userId)).thenReturn(false);
        when(objectMapper.writeValueAsString(contentPrefs)).thenReturn("[\"gaming\",\"tech\"]");
        when(objectMapper.writeValueAsString(notifications)).thenReturn("{\"email\":true,\"push\":false}");
        when(creatorProfileRepository.save(any(CreatorProfile.class))).thenReturn(savedProfile);
        when(objectMapper.readValue(eq("[\"gaming\",\"tech\"]"), any(TypeReference.class)))
            .thenReturn(contentPrefs);
        when(objectMapper.readValue(eq("{\"email\":true,\"push\":false}"), any(TypeReference.class)))
            .thenReturn(notifications);

        // When
        CreatorProfileResponse response = creatorProfileService.createProfile(userId, request);

        // Then
        assertNotNull(response);
        assertEquals("profile-id", response.id());
        assertEquals(userId, response.userId());
        assertEquals("TestCreator", response.displayName());
        assertEquals("Test bio", response.bio());
        assertEquals(CreatorCategory.GAMING, response.creatorCategory());

        verify(creatorProfileRepository).existsByUserId(userId);
        verify(creatorProfileRepository).save(any(CreatorProfile.class));
        verify(objectMapper).writeValueAsString(contentPrefs);
        verify(objectMapper).writeValueAsString(notifications);
    }

    @Test
    void createProfile_existingProfile_throwsProfileAlreadyExistsException() {
        // Given
        String userId = "test-user-id";
        CreateProfileRequest request = new CreateProfileRequest(
            "TestCreator",
            "Test bio",
            CreatorCategory.GAMING,
            null,
            null
        );

        when(creatorProfileRepository.existsByUserId(userId)).thenReturn(true);

        // When/Then
        ProfileAlreadyExistsException exception = assertThrows(
            ProfileAlreadyExistsException.class,
            () -> creatorProfileService.createProfile(userId, request)
        );

        assertEquals("Creator profile already exists.", exception.getMessage());
        verify(creatorProfileRepository).existsByUserId(userId);
        verify(creatorProfileRepository, never()).save(any());
    }

    @Test
    void getProfile_existingProfile_returnsResponse() throws JsonProcessingException {
        // Given
        String userId = "test-user-id";
        CreatorProfile profile = new CreatorProfile();
        profile.setId("profile-id");
        profile.setUserId(userId);
        profile.setDisplayName("TestCreator");
        profile.setBio("Test bio");
        profile.setCreatorCategory(CreatorCategory.GAMING);
        profile.setContentPreferences("[\"gaming\",\"tech\"]");
        profile.setNotificationSettings("{\"email\":true}");

        List<String> contentPrefs = List.of("gaming", "tech");
        Map<String, Object> notifications = Map.of("email", true);

        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(objectMapper.readValue(eq("[\"gaming\",\"tech\"]"), any(TypeReference.class)))
            .thenReturn(contentPrefs);
        when(objectMapper.readValue(eq("{\"email\":true}"), any(TypeReference.class)))
            .thenReturn(notifications);

        // When
        CreatorProfileResponse response = creatorProfileService.getProfile(userId);

        // Then
        assertNotNull(response);
        assertEquals("profile-id", response.id());
        assertEquals(userId, response.userId());
        assertEquals("TestCreator", response.displayName());
        assertEquals("Test bio", response.bio());
        assertEquals(CreatorCategory.GAMING, response.creatorCategory());
        assertEquals(contentPrefs, response.contentPreferences());
        assertEquals(notifications, response.notificationSettings());
    }

    @Test
    void getProfile_noProfile_throwsResourceNotFoundException() {
        // Given
        String userId = "test-user-id";

        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // When/Then
        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> creatorProfileService.getProfile(userId)
        );

        assertEquals("Creator profile not found.", exception.getMessage());
    }

    @Test
    void updateProfile_existingProfile_updatesOnlyProvidedFields() throws JsonProcessingException {
        // Given
        String userId = "test-user-id";
        CreatorProfile existingProfile = new CreatorProfile();
        existingProfile.setId("profile-id");
        existingProfile.setUserId(userId);
        existingProfile.setDisplayName("OldName");
        existingProfile.setBio("Old bio");
        existingProfile.setCreatorCategory(CreatorCategory.TECH);

        UpdateProfileRequest request = new UpdateProfileRequest(
            "NewName",  // only updating displayName
            null,       // not updating bio
            null,       // not updating category
            null,       // not updating content preferences
            null        // not updating notification settings
        );

        CreatorProfile updatedProfile = new CreatorProfile();
        updatedProfile.setId("profile-id");
        updatedProfile.setUserId(userId);
        updatedProfile.setDisplayName("NewName");
        updatedProfile.setBio("Old bio");  // unchanged
        updatedProfile.setCreatorCategory(CreatorCategory.TECH);  // unchanged

        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existingProfile));
        when(creatorProfileRepository.save(existingProfile)).thenReturn(updatedProfile);

        // When
        CreatorProfileResponse response = creatorProfileService.updateProfile(userId, request);

        // Then
        assertNotNull(response);
        assertEquals("NewName", response.displayName());
        assertEquals("Old bio", response.bio());  // Should remain unchanged
        assertEquals(CreatorCategory.TECH, response.creatorCategory());  // Should remain unchanged

        verify(creatorProfileRepository).findByUserId(userId);
        verify(creatorProfileRepository).save(existingProfile);
        // Verify that only displayName was set
        assertEquals("NewName", existingProfile.getDisplayName());
        assertEquals("Old bio", existingProfile.getBio());
    }

    @Test
    void updateProfile_noProfile_throwsResourceNotFoundException() {
        // Given
        String userId = "test-user-id";
        UpdateProfileRequest request = new UpdateProfileRequest(
            "NewName", null, null, null, null
        );

        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // When/Then
        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> creatorProfileService.updateProfile(userId, request)
        );

        assertEquals("Creator profile not found.", exception.getMessage());
    }

    @Test
    void saveProfileImage_validJpeg_savesAndUpdatesUrl() throws Exception {
        // Given
        String userId = "test-user-id";
        MultipartFile file = mock(MultipartFile.class);
        byte[] fileBytes = "fake image data".getBytes();

        CreatorProfile profile = new CreatorProfile();
        profile.setId("profile-id");
        profile.setUserId(userId);

        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(1024L);  // 1KB, well under 2MB limit
        when(file.getBytes()).thenReturn(fileBytes);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(creatorProfileRepository.save(profile)).thenReturn(profile);

        // When
        ProfileImageResponse response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.createDirectories(any(Path.class))).thenReturn(mock(Path.class));
            mockedFiles.when(() -> Files.write(any(Path.class), any(byte[].class))).thenReturn(mock(Path.class));
            response = creatorProfileService.saveProfileImage(userId, file);
        }

        // Then
        assertNotNull(response);
        assertNotNull(response.profileImageUrl());
        assertTrue(response.profileImageUrl().contains("uploads/profile-images/" + userId + "/"));
        assertTrue(response.profileImageUrl().endsWith(".jpg"));

        verify(file).getContentType();
        verify(file).getSize();
        verify(file).getBytes();
        verify(creatorProfileRepository).findByUserId(userId);
        verify(creatorProfileRepository).save(profile);
    }

    @Test
    void saveProfileImage_invalidMimeType_throwsIllegalArgumentException() {
        // Given
        String userId = "test-user-id";
        MultipartFile file = mock(MultipartFile.class);

        when(file.getContentType()).thenReturn("text/plain");

        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> creatorProfileService.saveProfileImage(userId, file)
        );

        assertEquals("Invalid image type. Allowed: JPEG, PNG, WEBP.", exception.getMessage());
    }

    @Test
    void saveProfileImage_fileTooLarge_throwsIllegalArgumentException() {
        // Given
        String userId = "test-user-id";
        MultipartFile file = mock(MultipartFile.class);

        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(3L * 1024 * 1024);  // 3MB, over the 2MB limit

        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> creatorProfileService.saveProfileImage(userId, file)
        );

        assertEquals("Image size must not exceed 2 MB.", exception.getMessage());
    }

    @Test
    void saveProfileImage_noProfile_throwsResourceNotFoundException() {
        // Given
        String userId = "test-user-id";
        MultipartFile file = mock(MultipartFile.class);

        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(1024L);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // When/Then
        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> creatorProfileService.saveProfileImage(userId, file)
        );

        assertEquals("Creator profile not found.", exception.getMessage());
    }
}