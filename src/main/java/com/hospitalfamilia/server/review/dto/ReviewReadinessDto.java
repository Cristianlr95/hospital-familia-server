package com.hospitalfamilia.server.review.dto;

import java.time.Instant;
import java.util.List;

public record ReviewReadinessDto(
    Instant generatedAt,
    long activePatientCount,
    long pendingLinkRequestCount,
    long approvedLinkCount,
    long upcomingEventCount,
    long unreadNotificationCount,
    int passedChecks,
    int totalChecks,
    List<ReviewReadinessCheckDto> checks
) {
}
