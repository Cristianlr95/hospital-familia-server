package com.hospitalfamilia.server.activity.dto;

import java.time.Instant;
import java.util.UUID;

public record ActivityFeedItemDto(
    String audience,
    String kind,
    Instant occurredAt,
    UUID patientPublicId,
    String patientDisplayName,
    String title,
    String message,
    String status,
    String actorName
) {
}
