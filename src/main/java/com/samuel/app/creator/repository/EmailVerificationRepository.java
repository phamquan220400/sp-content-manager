package com.samuel.app.creator.repository;

import com.samuel.app.creator.model.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, String> {
    Optional<EmailVerification> findByToken(String token);
    void deleteByToken(String token);
}
