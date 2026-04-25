package com.samuel.app.creator.dto;

public record NavigationItem(
        String label,
        String url,
        boolean enabled,
        String statusMessage,
        NavigationIcon icon
) {}
