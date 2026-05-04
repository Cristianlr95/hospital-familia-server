package com.hospitalfamilia.server.linking.dto;

import com.hospitalfamilia.server.linking.entity.LinkStatus;
import java.time.Instant;
import java.util.UUID;

public record PendingLinkRequestDto(
    Long id,
    LinkStatus status,
    Instant requestedAt,
    String tutorEmail,
    String tutorName,
    UUID patientPublicId,
    String patientDisplayName
) {
}
