package com.hospitalfamilia.server.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RevokeOtherSessionsRequest(
    @NotBlank String refreshToken
) {
}
