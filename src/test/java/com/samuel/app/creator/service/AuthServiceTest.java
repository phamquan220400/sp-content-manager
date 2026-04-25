package com.samuel.app.creator.service;

import com.samuel.app.creator.dto.AuthResponse;
import com.samuel.app.creator.dto.LoginRequest;
import com.samuel.app.creator.dto.LogoutRequest;
import com.samuel.app.creator.dto.RefreshRequest;
import com.samuel.app.creator.model.User;
import com.samuel.app.creator.repository.UserRepository;
import com.samuel.app.exceptions.InvalidTokenException;
import com.samuel.app.shared.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-21T10:00:00Z"), ZoneOffset.UTC);

    private AuthService authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authenticationManager, userRepository, jwtUtil,
                redisTemplate, fixedClock, 604800L, 5, 15);

        activeUser = new User();
        activeUser.setId("user-id-123");
        activeUser.setEmail("user@example.com");
        activeUser.setPassword("encoded-password");
        activeUser.setStatus(User.UserStatus.ACTIVE);
        activeUser.setFailedLoginAttempts(0);
        activeUser.setLockedUntil(null);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // --- login() ---

    @Test
    void login_validCredentials_returnsAuthResponse() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser));
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(jwtUtil.generateAccessToken("user-id-123", "user@example.com")).thenReturn("access-token");
        when(jwtUtil.getAccessTokenExpirySeconds()).thenReturn(900L);

        AuthResponse response = authService.login(new LoginRequest("user@example.com", "password"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.expiresIn()).isEqualTo(900L);
        verify(valueOps).set(startsWith("refresh:"), eq("user-id-123"), any());
        verify(userRepository, atLeastOnce()).save(activeUser);
        assertThat(activeUser.getFailedLoginAttempts()).isZero();
        assertThat(activeUser.getLockedUntil()).isNull();
    }

    @Test
    void login_pendingUser_throwsDisabledException() {
        User pendingUser = new User();
        pendingUser.setId("pending-id");
        pendingUser.setEmail("user@example.com");
        pendingUser.setStatus(User.UserStatus.PENDING);
        // PENDING check now happens before authenticationManager.authenticate()
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(pendingUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "password")))
                .isInstanceOf(org.springframework.security.authentication.DisabledException.class)
                .hasMessageContaining("verify your email");

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_invalidPassword_throwsBadCredentialsExceptionAndIncrementsCount() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository).save(activeUser);
        assertThat(activeUser.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(activeUser.getLockedUntil()).isNull();
    }

    @Test
    void login_exceedsLockoutThreshold_setsLockedUntilAndThrowsLockedException() {
        activeUser.setFailedLoginAttempts(4); // one more will hit threshold of 5
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(activeUser.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(activeUser.getLockedUntil()).isNotNull();
        // locked until = fixedClock now + 15 min
        LocalDateTime expectedLockout = LocalDateTime.now(fixedClock).plusMinutes(15);
        assertThat(activeUser.getLockedUntil()).isEqualTo(expectedLockout);
    }

    @Test
    void login_lockedAccount_throwsLockedExceptionWithoutIncrement() {
        activeUser.setFailedLoginAttempts(5);
        // locked until 30 minutes in the future relative to fixed clock
        activeUser.setLockedUntil(LocalDateTime.now(fixedClock).plusMinutes(30));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "any")))
                .isInstanceOf(LockedException.class)
                .hasMessageContaining("temporarily locked");

        // authenticate should never be called
        verify(authenticationManager, never()).authenticate(any());
        // failed_login_attempts should NOT be incremented
        assertThat(activeUser.getFailedLoginAttempts()).isEqualTo(5);
    }

    @Test
    void login_successResetsFailedAttempts() {
        activeUser.setFailedLoginAttempts(3);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser));
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(jwtUtil.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtUtil.getAccessTokenExpirySeconds()).thenReturn(900L);

        authService.login(new LoginRequest("user@example.com", "password"));

        assertThat(activeUser.getFailedLoginAttempts()).isZero();
        assertThat(activeUser.getLockedUntil()).isNull();
    }

    // --- refreshToken() ---

    @Test
    void refreshToken_validToken_returnsNewAuthResponse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:old-refresh-token")).thenReturn("user-id-123");
        when(userRepository.findById("user-id-123")).thenReturn(Optional.of(activeUser));
        when(jwtUtil.generateAccessToken("user-id-123", "user@example.com")).thenReturn("new-access-token");
        when(jwtUtil.getAccessTokenExpirySeconds()).thenReturn(900L);

        AuthResponse response = authService.refreshToken(new RefreshRequest("old-refresh-token"));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotEqualTo("old-refresh-token");
        verify(redisTemplate).delete("refresh:old-refresh-token");
        verify(valueOps).set(startsWith("refresh:"), eq("user-id-123"), any());
    }

    @Test
    void refreshToken_invalidToken_throwsInvalidTokenException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:bad-token")).thenReturn(null);

        assertThatThrownBy(() -> authService.refreshToken(new RefreshRequest("bad-token")))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Invalid or expired refresh token.");
    }

    // --- logout() ---

    @Test
    void logout_validToken_deletesFromRedis() {
        authService.logout(new LogoutRequest("some-refresh-token"));

        verify(redisTemplate).delete("refresh:some-refresh-token");
    }
}
