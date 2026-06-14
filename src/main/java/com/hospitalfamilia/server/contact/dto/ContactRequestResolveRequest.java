package com.hospitalfamilia.server.contact.dto;

import jakarta.validation.constraints.Size;

public record ContactRequestResolveRequest(
    @Size(max = 500) String note
) {
}
