package com.samuel.app.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Instagram OAuth integration.
 * Maps application.yml instagram.* properties to typed configuration.
 * Uses Meta Graph API (Business/Creator flow).
 */
@Configuration
@ConfigurationProperties(prefix = "instagram")
public class InstagramProperties {
    
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getClientSecret() {
        return clientSecret;
    }
    
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
    
    public String getRedirectUri() {
        return redirectUri;
    }
    
    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }
}