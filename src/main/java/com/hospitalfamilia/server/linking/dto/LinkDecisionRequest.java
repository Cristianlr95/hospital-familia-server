package com.hospitalfamilia.server.linking.dto;

import jakarta.validation.constraints.Size;

public record LinkDecisionRequest(
    @Size(max = 280) String reason
) {
}
