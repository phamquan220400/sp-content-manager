package com.samuel.app.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.app.config.TestRedisConfig;
import com.samuel.app.config.TestMailConfig;
import com.samuel.app.creator.dto.AuthResponse;
import com.samuel.app.creator.dto.LoginRequest;
import com.samuel.app.creator.dto.LogoutRequest;
import com.samuel.app.creator.dto.RefreshRequest;
import com.samuel.app.creator.dto.RegistrationRequest;
import com.samuel.app.creator.model.User;
import com.samuel.app.creator.repository.UserRepository;
import com.samuel.app.shared.controller.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the complete authentication workflow.
 * Tests the entire API stack from HTTP request to database persistence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import({TestRedisConfig.class, TestMailConfig.class})
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private User activeUser;
    private User pendingUser;
    private User lockedUser;

    @BeforeEach
    void setUp() {
        // Clear and setup test users (Redis clearing handled by mock)
        userRepository.deleteAll();
        
        // Active user (verified email)
        activeUser = new User();
        activeUser.setId("active-user-id");
        activeUser.setEmail("active@example.com");
        activeUser.setPassword(passwordEncoder.encode("password123"));
        activeUser.setStatus(User.UserStatus.ACTIVE);
        activeUser.setFailedLoginAttempts(0);
        activeUser.setLockedUntil(null);
        userRepository.save(activeUser);

        // Pending user (unverified email)
        pendingUser = new User();
        pendingUser.setId("pending-user-id");
        pendingUser.setEmail("pending@example.com");
        pendingUser.setPassword(passwordEncoder.encode("password123"));
        pendingUser.setStatus(User.UserStatus.PENDING);
        pendingUser.setFailedLoginAttempts(0);
        pendingUser.setLockedUntil(null);
        userRepository.save(pendingUser);

        // Locked user (too many failed attempts)
        lockedUser = new User();
        lockedUser.setId("locked-user-id");
        lockedUser.setEmail("locked@example.com");
        lockedUser.setPassword(passwordEncoder.encode("password123"));
        lockedUser.setStatus(User.UserStatus.LOCKED);
        lockedUser.setFailedLoginAttempts(5);
        lockedUser.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(10));
        userRepository.save(lockedUser);
    }

    // ===============================
    // REGISTRATION E2E TESTS
    // ===============================

    @Test
    void registerLoginWorkflow_newUser_shouldCompleteSuccessfully() throws Exception {
        RegistrationRequest registrationRequest = new RegistrationRequest(
                "newuser@example.com", "password123", "password123");

        // 1. Register new user
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Registration successful. Please check your email to verify your account."));

        // 2. Verify user exists in database with PENDING status
        User savedUser = userRepository.findByEmail("newuser@example.com").orElseThrow();
        assertThat(savedUser.getStatus()).isEqualTo(User.UserStatus.PENDING);
        assertThat(passwordEncoder.matches("password123", savedUser.getPassword())).isTrue();

        // 3. Attempt login before verification should fail
        LoginRequest loginRequest = new LoginRequest("newuser@example.com", "password123");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Please verify your email address before logging in."));
    }

    @Test
    void registerDuplicateEmail_shouldReturnConflict() throws Exception {
        RegistrationRequest duplicateRequest = new RegistrationRequest(
                "active@example.com", "password123", "password123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email address is already registered."));
    }

    // ===============================
    // LOGIN E2E TESTS
    // ===============================

    @Test
    void loginWorkflow_validActiveUser_shouldReturnTokens() throws Exception {
        LoginRequest loginRequest = new LoginRequest("active@example.com", "password123");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andReturn();

        // Parse response and validate tokens
        String responseBody = result.getResponse().getContentAsString();
        ApiResponse<AuthResponse> response = objectMapper.readValue(responseBody, 
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, AuthResponse.class));
        
        AuthResponse authResponse = response.data();
        assertThat(authResponse.accessToken()).isNotBlank();
        assertThat(authResponse.refreshToken()).isNotBlank();

        // Verify refresh token stored in Redis
        String userId = redisTemplate.opsForValue().get("refresh:" + authResponse.refreshToken());
        assertThat(userId).isEqualTo("active-user-id");

        // Verify failed login attempts reset
        User updatedUser = userRepository.findById("active-user-id").orElseThrow();
        assertThat(updatedUser.getFailedLoginAttempts()).isZero();
        assertThat(updatedUser.getLockedUntil()).isNull();
    }

    @Test
    void loginPendingUser_shouldReturnForbidden() throws Exception {
        LoginRequest loginRequest = new LoginRequest("pending@example.com", "password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Please verify your email address before logging in."));
    }

    @Test
    void loginInvalidCredentials_shouldReturnUnauthorizedAndIncrementFailures() throws Exception {
        LoginRequest loginRequest = new LoginRequest("active@example.com", "wrongpassword");

        // First failed attempt
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));

        // Verify failed attempt counter incremented
        User updatedUser = userRepository.findById("active-user-id").orElseThrow();
        assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void loginLockedAccount_shouldReturnUnauthorized() throws Exception {
        LoginRequest loginRequest = new LoginRequest("locked@example.com", "password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Account temporarily locked. Please try again later."));

        // Verify failed attempts not incremented when locked
        User updatedUser = userRepository.findById("locked-user-id").orElseThrow();
        assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo(5); // unchanged
    }

    @Test
    void loginAccountLockout_shouldLockAfterThreshold() throws Exception {
        LoginRequest loginRequest = new LoginRequest("active@example.com", "wrongpassword");

        // Fail 5 times to trigger lockout (threshold is 5)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());
        }

        // Verify user is now locked
        User updatedUser = userRepository.findById("active-user-id").orElseThrow();
        assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(updatedUser.getLockedUntil()).isAfter(java.time.LocalDateTime.now());

        // Subsequent login attempt should return locked message
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Account temporarily locked. Please try again later."));
    }

    // ===============================
    // TOKEN REFRESH E2E TESTS
    // ===============================

    @Test
    void refreshTokenWorkflow_validToken_shouldReturnNewTokens() throws Exception {
        // First login to get tokens
        String accessToken = performLogin("active@example.com", "password123");
        String originalRefreshToken = getRefreshTokenFromLogin("active@example.com", "password123");

        // Use refresh token to get new tokens
        RefreshRequest refreshRequest = new RefreshRequest(originalRefreshToken);
        MvcResult result = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn();

        // Parse new tokens
        String responseBody = result.getResponse().getContentAsString();
        ApiResponse<AuthResponse> response = objectMapper.readValue(responseBody,
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, AuthResponse.class));
        AuthResponse newAuthResponse = response.data();

        // Verify tokens are different (token rotation)
        assertThat(newAuthResponse.refreshToken()).isNotEqualTo(originalRefreshToken);

        // Verify old refresh token deleted from Redis
        String oldTokenUser = redisTemplate.opsForValue().get("refresh:" + originalRefreshToken);
        assertThat(oldTokenUser).isNull();

        // Verify new refresh token stored in Redis
        String newTokenUser = redisTemplate.opsForValue().get("refresh:" + newAuthResponse.refreshToken());
        assertThat(newTokenUser).isEqualTo("active-user-id");
    }

    @Test
    void refreshInvalidToken_shouldReturnUnauthorized() throws Exception {
        RefreshRequest invalidRefreshRequest = new RefreshRequest("invalid-refresh-token");

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRefreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token."));
    }

    // ===============================
    // LOGOUT E2E TESTS
    // ===============================

    @Test
    void logoutWorkflow_validRefreshToken_shouldDeleteFromRedis() throws Exception {
        // Login and get refresh token
        String refreshToken = getRefreshTokenFromLogin("active@example.com", "password123");

        // Verify token exists in Redis before logout
        String userId = redisTemplate.opsForValue().get("refresh:" + refreshToken);
        assertThat(userId).isEqualTo("active-user-id");

        // Logout
        LogoutRequest logoutRequest = new LogoutRequest(refreshToken);
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logged out successfully."));

        // Verify token deleted from Redis
        String deletedToken = redisTemplate.opsForValue().get("refresh:" + refreshToken);
        assertThat(deletedToken).isNull();
    }

    @Test
    void logoutInvalidToken_shouldStillReturnSuccess() throws Exception {
        // Logout with non-existent token should be idempotent
        LogoutRequest logoutRequest = new LogoutRequest("non-existent-token");
        
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logged out successfully."));
    }

    // ===============================
    // API VALIDATION E2E TESTS  
    // ===============================

    @Test
    void authEndpoints_invalidJsonPayload_shouldReturnBadRequest() throws Exception {
        String malformedJson = "{\"email\":\"incomplete";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"));
    }

    @Test
    void authEndpoints_missingRequiredFields_shouldReturnBadRequest() throws Exception {
        String incompleteRequest = "{\"email\":\"user@example.com\"}"; // missing password

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incompleteRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ===============================
    // HELPER METHODS
    // ===============================

    private String performLogin(String email, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ApiResponse<AuthResponse> response = objectMapper.readValue(responseBody,
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, AuthResponse.class));
        return response.data().accessToken();
    }

    private String getRefreshTokenFromLogin(String email, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ApiResponse<AuthResponse> response = objectMapper.readValue(responseBody,
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, AuthResponse.class));
        return response.data().refreshToken();
    }
}