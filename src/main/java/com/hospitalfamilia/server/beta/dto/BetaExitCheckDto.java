package com.hospitalfamilia.server.beta.dto;

import java.time.Instant;

public record BetaExitCheckDto(
    Long id,
    String key,
    String label,
    String description,
    int sortOrder,
    boolean completed,
    String notes,
    String completedByFullName,
    Instant completedAt,
    Instant updatedAt
) {
}
