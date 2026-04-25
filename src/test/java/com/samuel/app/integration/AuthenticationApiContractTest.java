package com.samuel.app.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.app.creator.dto.LoginRequest;
import com.samuel.app.creator.dto.RegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API Contract tests for authentication endpoints.
 * Validates API response structures and error codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerEndpoint_validRequest_shouldReturnStandardApiResponse() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "contracttest@example.com", "password123", "password123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").isBoolean())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void loginEndpoint_invalidRequest_shouldReturnStandardErrorResponse() throws Exception {
        LoginRequest request = new LoginRequest("nonexistent@example.com", "wrongpassword");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").isBoolean())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.message").value("Invalid email or password."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void refreshEndpoint_invalidToken_shouldReturnUnauthorized() throws Exception {
        String requestBody = "{\"refreshToken\":\"invalid-token\"}";

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void logoutEndpoint_anyToken_shouldReturnSuccess() throws Exception {
        String requestBody = "{\"refreshToken\":\"any-token\"}";

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logged out successfully."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void allAuthEndpoints_malformedJson_shouldReturnBadRequest() throws Exception {
        String malformedJson = "{\"email\":\"incomplete";

        String[] endpoints = {"/auth/register", "/auth/login", "/auth/refresh", "/auth/logout"};
        
        for (String endpoint : endpoints) {
            mockMvc.perform(post(endpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    @Test
    void allAuthEndpoints_emptyBody_shouldReturnBadRequest() throws Exception {
        String[] endpoints = {"/auth/register", "/auth/login", "/auth/refresh", "/auth/logout"};
        
        for (String endpoint : endpoints) {
            mockMvc.perform(post(endpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void verifyEndpoint_missingToken_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/auth/verify"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Required parameter 'token' is missing"));
    }

    @Test
    void authEndpoints_wrongHttpMethod_shouldReturnMethodNotAllowed() throws Exception {
        // POST endpoints should not accept GET
        mockMvc.perform(get("/auth/register"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/auth/refresh"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/auth/logout"))
                .andExpect(status().isMethodNotAllowed());

        // GET endpoint should not accept POST without token param
        mockMvc.perform(post("/auth/verify"))
                .andExpect(status().isMethodNotAllowed());
    }
}