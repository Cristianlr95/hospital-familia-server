package com.hospitalfamilia.server.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionDto(
    UUID sessionId,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt,
    Instant revokedAt,
    boolean revoked,
    boolean current
) {
}
