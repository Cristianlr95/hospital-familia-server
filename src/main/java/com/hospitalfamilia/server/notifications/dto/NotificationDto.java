package com.hospitalfamilia.server.notifications.dto;

import com.hospitalfamilia.server.notifications.entity.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
    Long id,
    NotificationType type,
    UUID patientPublicId,
    String patientDisplayName,
    String title,
    String message,
    boolean read,
    Instant readAt,
    Instant createdAt
) {
}
