package com.hospitalfamilia.server.patientstatus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatientStatusUpdateRequest(
    @NotBlank(message = "El estado visible es obligatorio")
    @Size(max = 80, message = "El estado visible debe tener maximo 80 caracteres")
    String careStatus,
    @Size(max = 120, message = "El servicio debe tener maximo 120 caracteres")
    String currentService,
    @Size(max = 120, message = "La ubicacion debe tener maximo 120 caracteres")
    String currentLocation,
    @Size(max = 220, message = "El resumen debe tener maximo 220 caracteres")
    String summary
) {
}
