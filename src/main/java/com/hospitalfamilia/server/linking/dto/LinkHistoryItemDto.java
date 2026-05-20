package com.hospitalfamilia.server.linking.dto;

import com.hospitalfamilia.server.linking.entity.LinkStatus;
import java.time.Instant;
import java.util.UUID;

public record LinkHistoryItemDto(
    Long id,
    LinkStatus status,
    Instant requestedAt,
    Instant decidedAt,
    String decisionReason,
    String tutorEmail,
    String tutorFullName,
    UUID patientPublicId,
    String patientDisplayName,
    String decidedByEmail,
    String decidedByFullName
) {
}
