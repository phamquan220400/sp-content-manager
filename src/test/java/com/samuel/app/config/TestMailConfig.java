package com.samuel.app.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.Mockito.*;

/**
 * Test configuration to mock mail dependencies for integration testing.
 * This allows registration tests to run without requiring actual email sending.
 */
@TestConfiguration
public class TestMailConfig {
    
    /**
     * Mock JavaMailSender to prevent mail sending failures in tests.
     * Registration workflow can complete without actual email delivery.
     */
    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        
        // Configure mail operations to succeed without actually sending
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        
        return mailSender;
    }
}