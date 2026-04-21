package com.samuel.app.creator.controller;

import com.samuel.app.creator.dto.RegistrationRequest;
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

    public AuthController(UserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
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
}
