package com.samuel.app.platform.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenEncryptionService.
 * Tests AES-256-GCM encryption/decryption functionality.
 */
@ExtendWith(MockitoExtension.class)
class TokenEncryptionServiceTest {
    
    private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]); // Valid 32-byte key
    private TokenEncryptionService tokenEncryptionService;
    
    @BeforeEach
    void setUp() {
        tokenEncryptionService = new TokenEncryptionService(TEST_KEY);
    }
    
    @Test
    void should_encrypt_and_decrypt_when_valid_plaintext_then_round_trip_succeeds() {
        // Given
        String plaintext = "sensitive-access-token-12345";
        
        // When
        String encrypted = tokenEncryptionService.encrypt(plaintext);
        String decrypted = tokenEncryptionService.decrypt(encrypted);
        
        // Then
        assertEquals(plaintext, decrypted);
        assertNotEquals(plaintext, encrypted);
    }
    
    @Test
    void should_throw_when_decrypt_invalid_ciphertext() {
        // Given
        String invalidCiphertext = "invalid-base64-data";
        
        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            tokenEncryptionService.decrypt(invalidCiphertext);
        });
    }
    
    @Test
    void should_throw_when_decrypt_tampered_ciphertext() {
        // Given
        String plaintext = "original-token";
        String encrypted = tokenEncryptionService.encrypt(plaintext);
        
        // Tamper with the encrypted data by changing last character
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
        encryptedBytes[encryptedBytes.length - 1] = (byte) (encryptedBytes[encryptedBytes.length - 1] ^ 1);
        String tamperedEncrypted = Base64.getEncoder().encodeToString(encryptedBytes);
        
        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            tokenEncryptionService.decrypt(tamperedEncrypted);
        });
    }
    
    @Test
    void should_produce_different_ciphertext_when_same_plaintext_encrypted_twice() {
        // Given
        String plaintext = "same-token";
        
        // When
        String encrypted1 = tokenEncryptionService.encrypt(plaintext);
        String encrypted2 = tokenEncryptionService.encrypt(plaintext);
        
        // Then
        assertNotEquals(encrypted1, encrypted2); // Different due to random IV
        assertEquals(plaintext, tokenEncryptionService.decrypt(encrypted1));
        assertEquals(plaintext, tokenEncryptionService.decrypt(encrypted2));
    }
    
    @Test
    void should_throw_when_encryption_key_is_wrong_size() {
        // Given
        String wrongSizeKey = Base64.getEncoder().encodeToString(new byte[16]); // Only 16 bytes (128-bit)
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            new TokenEncryptionService(wrongSizeKey);
        });
    }
    
    @Test
    void should_handle_empty_plaintext() {
        // Given
        String emptyPlaintext = "";
        
        // When
        String encrypted = tokenEncryptionService.encrypt(emptyPlaintext);
        String decrypted = tokenEncryptionService.decrypt(encrypted);
        
        // Then
        assertEquals(emptyPlaintext, decrypted);
    }
    
    @Test
    void should_handle_unicode_plaintext() {
        // Given
        String unicodePlaintext = "token-with-émojis-🔑-and-中文";
        
        // When
        String encrypted = tokenEncryptionService.encrypt(unicodePlaintext);
        String decrypted = tokenEncryptionService.decrypt(encrypted);
        
        // Then
        assertEquals(unicodePlaintext, decrypted);
    }
}