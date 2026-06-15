package com.hospitalfamilia.server.beta.dto;

import jakarta.validation.constraints.Size;

public record BetaExitCheckUpdateRequest(
    boolean completed,
    @Size(max = 500) String notes
) {
}
