package com.hospitalfamilia.server.linking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LinkRequestCreateRequest(
    @NotBlank @Size(max = 40) String patientCode
) {
}
