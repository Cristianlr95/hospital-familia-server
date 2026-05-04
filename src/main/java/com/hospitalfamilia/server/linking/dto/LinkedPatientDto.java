package com.hospitalfamilia.server.linking.dto;

import java.util.UUID;

public record LinkedPatientDto(
    UUID patientPublicId,
    String displayName
) {
}
