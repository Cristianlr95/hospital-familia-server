package com.hospitalfamilia.server.beta.dto;

import java.time.Instant;
import java.util.List;

public record BetaExitChecklistDto(
    Instant generatedAt,
    int completedChecks,
    int totalChecks,
    int progressPercent,
    List<BetaExitCheckDto> checks
) {
}
