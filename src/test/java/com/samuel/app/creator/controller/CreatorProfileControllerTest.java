package com.samuel.app.creator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.app.creator.dto.CreateProfileRequest;
import com.samuel.app.creator.dto.CreatorProfileResponse;
import com.samuel.app.creator.dto.ProfileImageResponse;
import com.samuel.app.creator.dto.UpdateProfileRequest;
import com.samuel.app.creator.model.CreatorProfile.CreatorCategory;
import com.samuel.app.creator.service.CreatorProfileService;
import com.samuel.app.exceptions.ProfileAlreadyExistsException;
import com.samuel.app.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CreatorProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class CreatorProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreatorProfileService creatorProfileService;

    @Test
    @WithMockUser(username = "test-user-id")
    void createProfile_validRequest_returns201() throws Exception {
        // Given
        CreateProfileRequest request = new CreateProfileRequest(
            "TestCreator",
            "Test bio",
            CreatorCategory.GAMING,
            List.of("gaming", "tech"),
            Map.of("email", true, "push", false)
        );

        CreatorProfileResponse response = new CreatorProfileResponse(
            "profile-id",
            "test-user-id",
            "TestCreator",
            "Test bio",
            CreatorCategory.GAMING,
            List.of("gaming", "tech"),
            Map.of("email", true, "push", false),
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        when(creatorProfileService.createProfile(eq("test-user-id"), any(CreateProfileRequest.class)))
            .thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/v1/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value("profile-id"))
            .andExpect(jsonPath("$.data.userId").value("test-user-id"))
            .andExpect(jsonPath("$.data.displayName").value("TestCreator"))
            .andExpect(jsonPath("$.data.bio").value("Test bio"))
            .andExpect(jsonPath("$.data.creatorCategory").value("GAMING"));
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void createProfile_blankDisplayName_returns400() throws Exception {
        // Given
        CreateProfileRequest request = new CreateProfileRequest(
            "",  // blank displayName
            "Test bio",
            CreatorCategory.GAMING,
            null,
            null
        );

        // When/Then
        mockMvc.perform(post("/api/v1/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void createProfile_duplicateProfile_returns409() throws Exception {
        // Given
        CreateProfileRequest request = new CreateProfileRequest(
            "TestCreator",
            "Test bio",
            CreatorCategory.GAMING,
            null,
            null
        );

        when(creatorProfileService.createProfile(eq("test-user-id"), any(CreateProfileRequest.class)))
            .thenThrow(new ProfileAlreadyExistsException("Creator profile already exists."));

        // When/Then
        mockMvc.perform(post("/api/v1/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Creator profile already exists."));
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void getProfile_existingProfile_returns200() throws Exception {
        // Given
        CreatorProfileResponse response = new CreatorProfileResponse(
            "profile-id",
            "test-user-id",
            "TestCreator",
            "Test bio",
            CreatorCategory.GAMING,
            List.of("gaming", "tech"),
            Map.of("email", true),
            "uploads/profile-images/test-user-id/image.jpg",
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        when(creatorProfileService.getProfile("test-user-id")).thenReturn(response);

        // When/Then
        mockMvc.perform(get("/api/v1/profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value("profile-id"))
            .andExpect(jsonPath("$.data.userId").value("test-user-id"))
            .andExpect(jsonPath("$.data.displayName").value("TestCreator"))
            .andExpect(jsonPath("$.data.bio").value("Test bio"))
            .andExpect(jsonPath("$.data.creatorCategory").value("GAMING"))
            .andExpect(jsonPath("$.data.profileImageUrl").value("uploads/profile-images/test-user-id/image.jpg"));
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void getProfile_noProfile_returns404() throws Exception {
        // Given
        when(creatorProfileService.getProfile("test-user-id"))
            .thenThrow(new ResourceNotFoundException("Creator profile not found."));

        // When/Then
        mockMvc.perform(get("/api/v1/profile"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Creator profile not found."));
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void updateProfile_validPartialUpdate_returns200() throws Exception {
        // Given
        UpdateProfileRequest request = new UpdateProfileRequest(
            "UpdatedName",  // only updating displayName
            null,           // not updating other fields
            null,
            null,
            null
        );

        CreatorProfileResponse response = new CreatorProfileResponse(
            "profile-id",
            "test-user-id",
            "UpdatedName",
            "Original bio",  // unchanged
            CreatorCategory.GAMING,  // unchanged
            List.of("gaming"),  // unchanged
            Map.of("email", true),  // unchanged
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        when(creatorProfileService.updateProfile(eq("test-user-id"), any(UpdateProfileRequest.class)))
            .thenReturn(response);

        // When/Then
        mockMvc.perform(put("/api/v1/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.displayName").value("UpdatedName"))
            .andExpect(jsonPath("$.data.bio").value("Original bio"));
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void uploadImage_validJpeg_returns200() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-image.jpg",
            "image/jpeg",
            "fake image content".getBytes()
        );

        ProfileImageResponse response = new ProfileImageResponse(
            "uploads/profile-images/test-user-id/12345.jpg"
        );

        when(creatorProfileService.saveProfileImage(eq("test-user-id"), any()))
            .thenReturn(response);

        // When/Then
        mockMvc.perform(multipart("/api/v1/profile/image")
                .file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.profileImageUrl").value("uploads/profile-images/test-user-id/12345.jpg"));
    }

    @Test
    @WithMockUser(username = "test-user-id")
    void uploadImage_invalidType_returns400() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-file.txt",
            "text/plain",
            "not an image".getBytes()
        );

        when(creatorProfileService.saveProfileImage(eq("test-user-id"), any()))
            .thenThrow(new IllegalArgumentException("Invalid image type. Allowed: JPEG, PNG, WEBP."));

        // When/Then
        mockMvc.perform(multipart("/api/v1/profile/image")
                .file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Invalid image type. Allowed: JPEG, PNG, WEBP."));
    }
}