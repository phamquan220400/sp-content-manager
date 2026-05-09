package com.samuel.app.platform.config;

import com.samuel.app.platform.adapter.PlatformType;
import org.springframework.stereotype.Component;

/**
 * Common accessor for all platform API endpoint URLs and OAuth scopes.
 *
 * <p>Inject this single bean instead of using hardcoded URL constants in platform
 * services and adapters. Values are resolved from {@link PlatformEndpoints}, which
 * is bound to {@code platform.endpoints.*} in {@code application.yml}.</p>
 *
 * <p>Common methods cover the shared OAuth contract (auth URL, token URL, scopes).
 * Platform-specific typed accessors are also provided for endpoints that only exist
 * on one platform (e.g. YouTube upload, TikTok user-info).</p>
 */
@Component
public class PlatformEndpointResolver {

    private final PlatformEndpoints endpoints;

    public PlatformEndpointResolver(PlatformEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    // -------------------------------------------------------
    //  Common cross-platform accessors
    // -------------------------------------------------------

    /**
     * Returns the OAuth authorization / login URL for the given platform.
     */
    public String getOAuthUrl(PlatformType platform) {
        return switch (platform) {
            case YOUTUBE   -> endpoints.getYoutube().getOauthUrl();
            case TIKTOK    -> endpoints.getTiktok().getAuthUrl();
            case INSTAGRAM -> endpoints.getInstagram().getOauthUrl();
            case FACEBOOK  -> endpoints.getFacebook().getOauthUrl();
        };
    }

    /**
     * Returns the token exchange / refresh URL for the given platform.
     */
    public String getTokenUrl(PlatformType platform) {
        return switch (platform) {
            case YOUTUBE   -> endpoints.getYoutube().getTokenUrl();
            case TIKTOK    -> endpoints.getTiktok().getTokenUrl();
            case INSTAGRAM -> endpoints.getInstagram().getTokenUrl();
            case FACEBOOK  -> endpoints.getFacebook().getTokenUrl();
        };
    }

    /**
     * Returns the configured OAuth scopes string for the given platform.
     */
    public String getScopes(PlatformType platform) {
        return switch (platform) {
            case YOUTUBE   -> endpoints.getYoutube().getScopes();
            case TIKTOK    -> endpoints.getTiktok().getScopes();
            case INSTAGRAM -> endpoints.getInstagram().getScopes();
            case FACEBOOK  -> endpoints.getFacebook().getScopes();
        };
    }

    // -------------------------------------------------------
    //  Platform-specific typed accessors
    // -------------------------------------------------------

    /** All YouTube endpoint URLs. */
    public PlatformEndpoints.YouTube getYouTube() {
        return endpoints.getYoutube();
    }

    /** All TikTok endpoint URLs. */
    public PlatformEndpoints.TikTok getTikTok() {
        return endpoints.getTiktok();
    }

    /** All Instagram endpoint URLs. */
    public PlatformEndpoints.Instagram getInstagram() {
        return endpoints.getInstagram();
    }

    /** All Facebook endpoint URLs. */
    public PlatformEndpoints.Facebook getFacebook() {
        return endpoints.getFacebook();
    }
}
