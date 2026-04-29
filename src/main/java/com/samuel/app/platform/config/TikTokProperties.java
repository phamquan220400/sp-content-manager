package com.samuel.app.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for TikTok OAuth integration.
 * Maps application.yml tiktok.* properties to typed configuration.
 * Note: TikTok uses "client_key" (not "client_id") as the app identifier.
 */
@Configuration
@ConfigurationProperties(prefix = "tiktok")
public class TikTokProperties {

    private String clientKey;
    private String clientSecret;
    private String redirectUri;

    public String getClientKey() {
        return clientKey;
    }

    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
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
