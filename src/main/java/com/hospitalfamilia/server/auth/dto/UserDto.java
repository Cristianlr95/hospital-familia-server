package com.hospitalfamilia.server.auth.dto;

import java.util.Set;

public record UserDto(
    Long id,
    String email,
    String firstName,
    String lastName,
    String phoneNumber,
    Set<String> roles
) {
}
