package com.hospitalfamilia.server.events.dto;

import com.hospitalfamilia.server.events.entity.PatientEventStatus;
import com.hospitalfamilia.server.events.entity.PatientEventType;
import java.time.Instant;
import java.util.UUID;

public record PatientEventDto(
    Long id,
    UUID patientPublicId,
    String patientDisplayName,
    PatientEventType type,
    PatientEventStatus status,
    String title,
    String description,
    Instant scheduledAt,
    Integer estimatedDurationMinutes,
    String service,
    String location,
    String responsibleStaff,
    Instant createdAt,
    Instant updatedAt
) {
}
