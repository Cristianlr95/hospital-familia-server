package com.hospitalfamilia.server.notifications.controller;

import com.hospitalfamilia.server.common.dto.ApiResponse;
import com.hospitalfamilia.server.notifications.dto.NotificationDto;
import com.hospitalfamilia.server.notifications.service.NotificationCenterService;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasRole('TUTOR')")
public class NotificationController {

    private final NotificationCenterService notificationCenterService;

    public NotificationController(NotificationCenterService notificationCenterService) {
        this.notificationCenterService = notificationCenterService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationDto>>> myNotifications(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(
            "Notificaciones recientes",
            notificationCenterService.myNotifications(principal.getName())
        ));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationDto>> markRead(Principal principal, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
            "Notificacion marcada como leida",
            notificationCenterService.markRead(principal.getName(), id)
        ));
    }
}
