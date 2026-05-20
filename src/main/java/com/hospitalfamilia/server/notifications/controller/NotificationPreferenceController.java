package com.hospitalfamilia.server.notifications.controller;

import com.hospitalfamilia.server.common.dto.ApiResponse;
import com.hospitalfamilia.server.notifications.dto.NotificationPreferenceDto;
import com.hospitalfamilia.server.notifications.dto.NotificationPreferenceUpdateRequest;
import com.hospitalfamilia.server.notifications.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/preferences")
@PreAuthorize("hasRole('TUTOR')")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceDto>> getPreferences(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(
            "Preferencias de notificacion",
            preferenceService.getPreferences(principal.getName())
        ));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceDto>> updatePreferences(
        Principal principal,
        @Valid @RequestBody NotificationPreferenceUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Preferencias de notificacion actualizadas",
            preferenceService.updatePreferences(principal.getName(), request)
        ));
    }
}
