package com.hospitalfamilia.server.events.dto;

import com.hospitalfamilia.server.events.entity.PatientEventType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record PatientEventCreateRequest(
    @NotNull UUID patientPublicId,
    @NotNull PatientEventType type,
    @NotBlank @Size(max = 140) String title,
    @Size(max = 500) String description,
    @NotNull Instant scheduledAt,
    @Min(1) @Max(1440) Integer estimatedDurationMinutes,
    @Size(max = 120) String service,
    @Size(max = 120) String location,
    @Size(max = 160) String responsibleStaff
) {
}
