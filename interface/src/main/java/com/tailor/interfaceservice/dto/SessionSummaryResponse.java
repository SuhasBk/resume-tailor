package com.tailor.interfaceservice.dto;

import java.time.Instant;

/**
 * Lightweight projection of a {@link com.tailor.interfaceservice.entity.TailorSession}
 * returned when listing a user's history.
 */
public record SessionSummaryResponse(
        Long sessionId,
        String userId,
        String jobDescriptionSnippet,
        Instant createdAt,
        Instant updatedAt
) {}
