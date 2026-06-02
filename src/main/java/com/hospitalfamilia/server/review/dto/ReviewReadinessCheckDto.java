package com.hospitalfamilia.server.review.dto;

public record ReviewReadinessCheckDto(
    String key,
    String label,
    boolean passed,
    String detail
) {
}
