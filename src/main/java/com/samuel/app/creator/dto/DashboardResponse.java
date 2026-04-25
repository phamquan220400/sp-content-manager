package com.samuel.app.creator.dto;

public record DashboardResponse(
        String welcomeMessage,
        String currentDateTime,
        ProfileSummary profileSummary,
        NavigationMenu navigationMenu,
        RevenuePlaceholder revenuePlaceholder,
        AccountActions accountActions
) {}
