package com.hospitalfamilia.server.auth.dto;

public record PasswordResetRequestResponse(
    boolean accepted,
    String devResetToken
) {
}
