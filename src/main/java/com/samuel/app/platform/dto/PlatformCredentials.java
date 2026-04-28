package com.samuel.app.platform.dto;

import com.samuel.app.platform.adapter.PlatformType;

/**
 * Platform credentials used for OAuth authentication.
 * Contains the authorization code and redirect URI for platform connection.
 */
public record PlatformCredentials(
    PlatformType platformType,
    String authorizationCode,
    String redirectUri
) {}