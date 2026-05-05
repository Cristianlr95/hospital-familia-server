package com.hospitalfamilia.server.events.dto;

import com.hospitalfamilia.server.events.entity.PatientEventStatus;
import jakarta.validation.constraints.NotNull;

public record PatientEventStatusUpdateRequest(
    @NotNull PatientEventStatus status
) {
}
