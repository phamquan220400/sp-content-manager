package com.samuel.app.creator.controller;

import com.samuel.app.creator.dto.AuthResponse;
import com.samuel.app.creator.dto.LoginRequest;
import com.samuel.app.creator.dto.LogoutRequest;
import com.samuel.app.creator.dto.RefreshRequest;
import com.samuel.app.creator.dto.RegistrationRequest;
import com.samuel.app.creator.service.AuthService;
import com.samuel.app.creator.service.UserRegistrationService;
import com.samuel.app.shared.controller.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRegistrationService userRegistrationService;
    private final AuthService authService;

    public AuthController(UserRegistrationService userRegistrationService,
                          AuthService authService) {
        this.userRegistrationService = userRegistrationService;
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegistrationRequest request) {
        userRegistrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, null,
                        "Registration successful. Please check your email to verify your account.",
                        java.time.LocalDateTime.now()));
    }

    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verify(@RequestParam String token) {
        userRegistrationService.verifyEmail(token);
        return ResponseEntity.ok(new ApiResponse<>(true, null,
                "Email verified successfully. You can now log in.",
                java.time.LocalDateTime.now()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(authResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse authResponse = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.ok(authResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(new ApiResponse<>(true, null, "Logged out successfully.", java.time.LocalDateTime.now()));
    }
}
