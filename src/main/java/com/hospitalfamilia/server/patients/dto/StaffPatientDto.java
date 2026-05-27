package com.hospitalfamilia.server.patients.dto;

import java.time.Instant;
import java.util.UUID;

public record StaffPatientDto(
    UUID publicId,
    String displayName,
    String linkCode,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}
