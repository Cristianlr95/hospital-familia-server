package com.hospitalfamilia.server.activity.controller;

import com.hospitalfamilia.server.activity.dto.ActivityFeedItemDto;
import com.hospitalfamilia.server.activity.service.ActivityFeedService;
import com.hospitalfamilia.server.common.dto.ApiResponse;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    private final ActivityFeedService activityFeedService;

    public ActivityController(ActivityFeedService activityFeedService) {
        this.activityFeedService = activityFeedService;
    }

    @GetMapping("/tutor")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<ApiResponse<List<ActivityFeedItemDto>>> tutorFeed(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(
            "Actividad reciente del tutor",
            activityFeedService.tutorFeed(principal.getName())
        ));
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ActivityFeedItemDto>>> staffFeed() {
        return ResponseEntity.ok(ApiResponse.success(
            "Actividad reciente del staff",
            activityFeedService.staffFeed()
        ));
    }
}
