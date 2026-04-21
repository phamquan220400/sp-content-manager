package com.samuel.app.creator.service;

import com.samuel.app.creator.dto.RegistrationRequest;
import com.samuel.app.creator.model.EmailVerification;
import com.samuel.app.creator.model.User;
import com.samuel.app.creator.model.User.UserStatus;
import com.samuel.app.creator.repository.EmailVerificationRepository;
import com.samuel.app.creator.repository.UserRepository;
import com.samuel.app.exceptions.EmailAlreadyExistsException;
import com.samuel.app.shared.service.EmailService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public UserRegistrationService(UserRepository userRepository,
                                   EmailVerificationRepository emailVerificationRepository,
                                   BCryptPasswordEncoder passwordEncoder,
                                   EmailService emailService) {
        this.userRepository = userRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public void register(RegistrationRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email address is already registered.");
        }

        String userId = UUID.randomUUID().toString();
        User user = new User();
        user.setId(userId);
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.PENDING);
        userRepository.save(user);

        String token = UUID.randomUUID().toString();
        EmailVerification verification = new EmailVerification();
        verification.setId(UUID.randomUUID().toString());
        verification.setUserId(userId);
        verification.setToken(token);
        verification.setExpiresAt(LocalDateTime.now().plusHours(24));
        verification.setCreatedAt(LocalDateTime.now());
        emailVerificationRepository.save(verification);

        emailService.sendVerificationEmail(request.email(), token);
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerification verification = emailVerificationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token."));

        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            emailVerificationRepository.deleteByToken(token);
            throw new IllegalArgumentException("Invalid or expired verification token.");
        }

        User user = userRepository.findById(verification.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token."));

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        emailVerificationRepository.deleteByToken(token);
    }
}
