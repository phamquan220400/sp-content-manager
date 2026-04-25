package com.samuel.app.creator.dto;

public record AccountActions(
        String accountSettingsUrl,
        String editProfileUrl,
        String logoutUrl
) {}
