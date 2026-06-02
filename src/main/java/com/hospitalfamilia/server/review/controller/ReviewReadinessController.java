package com.hospitalfamilia.server.review.controller;

import com.hospitalfamilia.server.common.dto.ApiResponse;
import com.hospitalfamilia.server.review.dto.ReviewReadinessDto;
import com.hospitalfamilia.server.review.service.ReviewReadinessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/review-readiness")
public class ReviewReadinessController {

    private final ReviewReadinessService readinessService;

    public ReviewReadinessController(ReviewReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReviewReadinessDto>> currentReadiness() {
        return ResponseEntity.ok(ApiResponse.success("Checklist beta staff", readinessService.currentReadiness()));
    }
}
