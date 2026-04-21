package com.samuel.app.creator.service;

import com.samuel.app.creator.dto.RegistrationRequest;
import com.samuel.app.creator.model.EmailVerification;
import com.samuel.app.creator.model.User;
import com.samuel.app.creator.model.User.UserStatus;
import com.samuel.app.creator.repository.EmailVerificationRepository;
import com.samuel.app.creator.repository.UserRepository;
import com.samuel.app.exceptions.EmailAlreadyExistsException;
import com.samuel.app.shared.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserRegistrationService service;

    private RegistrationRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new RegistrationRequest("user@example.com", "password123", "password123");
    }

    // --- register() ---

    @Test
    void register_successfulRegistration_savesUserAndVerificationAndSendsEmail() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");

        service.register(validRequest);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("user@example.com");
        assertThat(savedUser.getPassword()).isEqualTo("hashed-password");
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(savedUser.getId()).isNotBlank();

        ArgumentCaptor<EmailVerification> verCaptor = ArgumentCaptor.forClass(EmailVerification.class);
        verify(emailVerificationRepository).save(verCaptor.capture());
        EmailVerification savedVerification = verCaptor.getValue();
        assertThat(savedVerification.getUserId()).isEqualTo(savedUser.getId());
        assertThat(savedVerification.getToken()).isNotBlank();
        assertThat(savedVerification.getExpiresAt()).isAfter(LocalDateTime.now());

        verify(emailService).sendVerificationEmail(eq("user@example.com"), anyString());
    }

    @Test
    void register_passwordMismatch_throwsIllegalArgumentException() {
        RegistrationRequest mismatch = new RegistrationRequest("user@example.com", "password123", "different");

        assertThatThrownBy(() -> service.register(mismatch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Passwords do not match.");

        verifyNoInteractions(userRepository, emailVerificationRepository, emailService);
    }

    @Test
    void register_duplicateEmail_throwsEmailAlreadyExistsException() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(validRequest))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("Email address is already registered.");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailVerificationRepository, emailService);
    }

    // --- verifyEmail() ---

    @Test
    void verifyEmail_validToken_activatesUserAndDeletesToken() {
        String token = "valid-token";
        User user = new User();
        user.setId("user-id");
        user.setStatus(UserStatus.PENDING);

        EmailVerification verification = new EmailVerification();
        verification.setUserId("user-id");
        verification.setToken(token);
        verification.setExpiresAt(LocalDateTime.now().plusHours(23));

        when(emailVerificationRepository.findByToken(token)).thenReturn(Optional.of(verification));
        when(userRepository.findById("user-id")).thenReturn(Optional.of(user));

        service.verifyEmail(token);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(user);
        verify(emailVerificationRepository).deleteByToken(token);
    }

    @Test
    void verifyEmail_invalidToken_throwsIllegalArgumentException() {
        when(emailVerificationRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyEmail("bad-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid or expired verification token.");
    }

    @Test
    void verifyEmail_expiredToken_deletesTokenAndThrowsIllegalArgumentException() {
        String token = "expired-token";
        EmailVerification verification = new EmailVerification();
        verification.setUserId("user-id");
        verification.setToken(token);
        verification.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(emailVerificationRepository.findByToken(token)).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> service.verifyEmail(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid or expired verification token.");

        verify(emailVerificationRepository).deleteByToken(token);
        verify(userRepository, never()).save(any());
    }
}
