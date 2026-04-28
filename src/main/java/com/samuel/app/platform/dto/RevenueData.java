package com.samuel.app.platform.dto;

import com.samuel.app.platform.adapter.PlatformType;

import java.math.BigDecimal;

/**
 * Revenue data for a specific platform user and time period.
 * Represents monetization metrics from platform APIs.
 */
public record RevenueData(
    String platformUserId,
    PlatformType platformType,
    BigDecimal amount,
    String currency,
    DateRange period
) {}