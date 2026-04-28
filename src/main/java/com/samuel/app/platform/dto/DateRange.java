package com.samuel.app.platform.dto;

import java.time.LocalDate;

/**
 * Date range specification for time-based queries.
 * Used for filtering data by date periods.
 */
public record DateRange(
    LocalDate startDate,
    LocalDate endDate
) {}