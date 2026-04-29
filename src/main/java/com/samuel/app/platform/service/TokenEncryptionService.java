package com.samuel.app.platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Service for encrypting and decrypting platform tokens using AES-256-GCM.
 * Provides authenticated encryption with random IV per operation.
 */
@Service
public class TokenEncryptionService {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits
    
    private final byte[] keyBytes;
    
    public TokenEncryptionService(@Value("${platform.token-encryption-key}") String keyBase64) {
        this.keyBytes = Base64.getDecoder().decode(keyBase64);
        if (this.keyBytes.length != 32) {
            throw new IllegalArgumentException("Encryption key must be 32 bytes (256 bits)");
        }
    }
    
    /**
     * Encrypts plaintext using AES-256-GCM with random IV.
     * @param plaintext the text to encrypt
     * @return Base64-encoded string containing IV + ciphertext + auth tag
     */
    public String encrypt(String plaintext) {
        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            // Initialize cipher
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, paramSpec);
            
            // Encrypt
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV + ciphertext (cipherText already includes auth tag)
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt token", e);
        }
    }
    
    /**
     * Decrypts encrypted token back to plaintext.
     * @param encrypted Base64-encoded encrypted string
     * @return decrypted plaintext
     * @throws IllegalStateException if decryption fails or auth tag is invalid (tampering detected)
     */
    public String decrypt(String encrypted) {
        try {
            // Decode from Base64
            byte[] combined = Base64.getDecoder().decode(encrypted);
            
            // Extract IV and ciphertext
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
            
            // Initialize cipher
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);
            
            // Decrypt
            byte[] plaintext = cipher.doFinal(cipherText);
            
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt token - possible tampering detected", e);
        }
    }
}