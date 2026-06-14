package com.hospitalfamilia.server.contact.dto;

import com.hospitalfamilia.server.contact.entity.ContactRequestStatus;
import java.time.Instant;
import java.util.UUID;

public record ContactRequestDto(
    Long id,
    ContactRequestStatus status,
    UUID patientPublicId,
    String patientDisplayName,
    String tutorEmail,
    String tutorFullName,
    String message,
    String resolutionNote,
    String resolvedByFullName,
    Instant resolvedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
