package com.samuel.app.creator.service;

import com.samuel.app.creator.dto.AuthResponse;
import com.samuel.app.creator.dto.LoginRequest;
import com.samuel.app.creator.dto.LogoutRequest;
import com.samuel.app.creator.dto.RefreshRequest;
import com.samuel.app.creator.model.User;
import com.samuel.app.creator.repository.UserRepository;
import com.samuel.app.exceptions.InvalidTokenException;
import com.samuel.app.shared.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    private final long refreshTokenExpirySeconds;
    private final int lockoutThreshold;
    private final int lockoutDurationMinutes;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtUtil jwtUtil,
            StringRedisTemplate redisTemplate,
            Clock clock,
            @Value("${app.auth.refresh-token-expiry}") long refreshTokenExpirySeconds,
            @Value("${app.auth.lockout-threshold}") int lockoutThreshold,
            @Value("${app.auth.lockout-duration}") int lockoutDurationMinutes) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.refreshTokenExpirySeconds = refreshTokenExpirySeconds;
        this.lockoutThreshold = lockoutThreshold;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, LockedException.class})
    public AuthResponse login(LoginRequest request) {
        // Single user lookup to prevent race conditions and improve performance
        User user = userRepository.findByEmail(request.email()).orElse(null);
        
        // Pre-check lockout if user exists
        if (user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now(clock))) {
            throw new LockedException("Account temporarily locked. Please try again later.");
        }

        // Pre-check PENDING status — DaoAuthenticationProvider wraps DisabledException in
        // InternalAuthenticationServiceException, which bypasses GlobalExceptionHandler.
        // Checking here ensures the correct 403 response.
        if (user != null && user.getStatus() == User.UserStatus.PENDING) {
            throw new DisabledException("Please verify your email address before logging in.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException ex) {
            // Only increment failed attempts if user exists (prevent user enumeration)
            if (user != null) {
                int attempts = user.getFailedLoginAttempts() + 1;
                user.setFailedLoginAttempts(attempts);
                if (attempts >= lockoutThreshold) {
                    user.setLockedUntil(LocalDateTime.now(clock).plusMinutes(lockoutDurationMinutes));
                }
                userRepository.save(user);
            }
            throw ex;
        }

        // At this point authentication succeeded, so user must exist
        if (user == null) {
            // This should never happen, but handle gracefully
            user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalStateException("User not found after successful authentication"));
        }

        // Reset failed attempts on successful login
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = UUID.randomUUID().toString();
        
        try {
            redisTemplate.opsForValue().set(
                    "refresh:" + refreshToken,
                    user.getId(),
                    Duration.ofSeconds(refreshTokenExpirySeconds)
            );
        } catch (Exception ex) {
            // Handle Redis connectivity issues during login
            throw new RuntimeException("Authentication service temporarily unavailable. Please try again later.");
        }

        return new AuthResponse(accessToken, refreshToken, jwtUtil.getAccessTokenExpirySeconds());
    }

    public AuthResponse refreshToken(RefreshRequest request) {
        String redisKey = "refresh:" + request.refreshToken();
        String userId;
        
        try {
            userId = redisTemplate.opsForValue().get(redisKey);
        } catch (Exception ex) {
            // Handle Redis connectivity issues gracefully
            throw new InvalidTokenException("Token service temporarily unavailable. Please try again later.");
        }
        
        if (userId == null) {
            throw new InvalidTokenException("Invalid or expired refresh token.");
        }

        try {
            redisTemplate.delete(redisKey);
        } catch (Exception ex) {
            // Log but don't fail - token rotation security is more important than cleanup
            // In production, consider using a circuit breaker pattern here
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired refresh token."));

        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = UUID.randomUUID().toString();
        
        try {
            redisTemplate.opsForValue().set(
                    "refresh:" + newRefreshToken,
                    user.getId(),
                    Duration.ofSeconds(refreshTokenExpirySeconds)
            );
        } catch (Exception ex) {
            // Handle Redis connectivity issues during token storage
            throw new InvalidTokenException("Token service temporarily unavailable. Please try again later.");
        }

        return new AuthResponse(newAccessToken, newRefreshToken, jwtUtil.getAccessTokenExpirySeconds());
    }

    public void logout(LogoutRequest request) {
        try {
            redisTemplate.delete("refresh:" + request.refreshToken());
        } catch (Exception ex) {
            // Redis failures during logout should not prevent successful logout response
            // Token will expire naturally if Redis cleanup fails
            // In production, consider logging this for monitoring
        }
    }
}
