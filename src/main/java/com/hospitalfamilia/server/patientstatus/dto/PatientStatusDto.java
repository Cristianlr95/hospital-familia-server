package com.hospitalfamilia.server.patientstatus.dto;

import java.time.Instant;
import java.util.UUID;

public record PatientStatusDto(
    UUID patientPublicId,
    String displayName,
    String careStatus,
    String currentService,
    String currentLocation,
    String summary,
    Instant updatedAt
) {
}
