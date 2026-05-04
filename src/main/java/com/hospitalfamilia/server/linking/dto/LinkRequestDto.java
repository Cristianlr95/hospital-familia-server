package com.hospitalfamilia.server.linking.dto;

import com.hospitalfamilia.server.linking.entity.LinkStatus;
import java.time.Instant;
import java.util.UUID;

public record LinkRequestDto(
    Long id,
    LinkStatus status,
    Instant requestedAt,
    Instant decidedAt,
    String decisionReason,
    UUID patientPublicId,
    String patientDisplayName
) {
}
