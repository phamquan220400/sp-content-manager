package com.samuel.app.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for YouTube OAuth integration.
 * Maps application.yml youtube.* properties to typed configuration.
 */
@ConfigurationProperties(prefix = "youtube")
public class YouTubeProperties {
    
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