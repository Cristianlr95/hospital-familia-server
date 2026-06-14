package com.hospitalfamilia.server.contact.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ContactRequestCreateRequest(
    @NotNull UUID patientPublicId,
    @NotBlank @Size(max = 500) String message
) {
}
