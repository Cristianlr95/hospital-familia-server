package com.hospitalfamilia.server.patients.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StaffPatientCreateRequest(
    @NotBlank @Size(max = 160) String displayName,
    @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "El codigo solo puede contener letras, numeros y guiones")
    String linkCode
) {
}
