package com.hospitalfamilia.server.auth.dto;

public record LoginResponse(
    String tokenType,
    String accessToken,
    String refreshToken,
    long expiresInMs,
    UserDto user
) {
}
