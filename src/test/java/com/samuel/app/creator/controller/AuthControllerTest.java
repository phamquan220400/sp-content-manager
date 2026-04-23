package com.samuel.app.creator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.app.creator.dto.AuthResponse;
import com.samuel.app.creator.dto.LoginRequest;
import com.samuel.app.creator.dto.LogoutRequest;
import com.samuel.app.creator.dto.RefreshRequest;
import com.samuel.app.creator.dto.RegistrationRequest;
import com.samuel.app.creator.service.AuthService;
import com.samuel.app.creator.service.UserRegistrationService;
import com.samuel.app.exceptions.EmailAlreadyExistsException;
import com.samuel.app.exceptions.InvalidTokenException;
import com.samuel.app.shared.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRegistrationService userRegistrationService;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    // --- POST /auth/register ---

    @Test
    void register_validPayload_returns201() throws Exception {
        RegistrationRequest request = new RegistrationRequest("user@example.com", "password123", "password123");
        doNothing().when(userRegistrationService).register(any());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration successful. Please check your email to verify your account."));
    }

    @Test
    void register_invalidPayload_blankEmail_returns400() throws Exception {
        String body = "{\"email\":\"\",\"password\":\"password123\",\"confirmPassword\":\"password123\"}";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_invalidPayload_passwordTooShort_returns400() throws Exception {
        String body = "{\"email\":\"user@example.com\",\"password\":\"short\",\"confirmPassword\":\"short\"}";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        RegistrationRequest request = new RegistrationRequest("user@example.com", "password123", "password123");
        doThrow(new EmailAlreadyExistsException("Email address is already registered."))
                .when(userRegistrationService).register(any());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email address is already registered."));
    }

    // --- GET /auth/verify ---

    @Test
    void verify_validToken_returns200() throws Exception {
        doNothing().when(userRegistrationService).verifyEmail("valid-token");

        mockMvc.perform(get("/auth/verify").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Email verified successfully. You can now log in."));
    }

    @Test
    void verify_invalidToken_returns400() throws Exception {
        doThrow(new IllegalArgumentException("Invalid or expired verification token."))
                .when(userRegistrationService).verifyEmail("bad-token");

        mockMvc.perform(get("/auth/verify").param("token", "bad-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired verification token."));
    }

    // --- POST /auth/login ---

    @Test
    void login_validRequest_returns200WithTokens() throws Exception {
        AuthResponse authResponse = new AuthResponse("access-token", "refresh-token", 900L);
        when(authService.login(any())).thenReturn(authResponse);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user@example.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(900));
    }

    @Test
    void login_blankEmail_returns400() throws Exception {
        String body = "{\"email\":\"\",\"password\":\"password123\"}";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void login_disabledUser_returns403() throws Exception {
        when(authService.login(any()))
                .thenThrow(new DisabledException("Please verify your email address before logging in."));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user@example.com", "password123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Please verify your email address before logging in."));
    }

    @Test
    void login_lockedUser_returns401() throws Exception {
        when(authService.login(any()))
                .thenThrow(new LockedException("Account temporarily locked. Please try again later."));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user@example.com", "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Account temporarily locked. Please try again later."));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user@example.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    // --- POST /auth/refresh ---

    @Test
    void refresh_validToken_returns200() throws Exception {
        AuthResponse authResponse = new AuthResponse("new-access-token", "new-refresh-token", 900L);
        when(authService.refreshToken(any())).thenReturn(authResponse);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("valid-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        when(authService.refreshToken(any()))
                .thenThrow(new InvalidTokenException("Invalid or expired refresh token."));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("bad-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token."));
    }

    // --- POST /auth/logout ---

    @Test
    void logout_validRequest_returns200() throws Exception {
        doNothing().when(authService).logout(any());

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest("some-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logged out successfully."));
    }
}
