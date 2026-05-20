package com.hospitalfamilia.server.linking.controller;

import com.hospitalfamilia.server.common.dto.ApiResponse;
import com.hospitalfamilia.server.linking.dto.LinkDecisionRequest;
import com.hospitalfamilia.server.linking.dto.LinkHistoryItemDto;
import com.hospitalfamilia.server.linking.dto.LinkRequestCreateRequest;
import com.hospitalfamilia.server.linking.dto.LinkRequestDto;
import com.hospitalfamilia.server.linking.dto.LinkedPatientDto;
import com.hospitalfamilia.server.linking.dto.PendingLinkRequestDto;
import com.hospitalfamilia.server.linking.service.LinkingService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/linking")
public class LinkingController {

    private final LinkingService linkingService;

    public LinkingController(LinkingService linkingService) {
        this.linkingService = linkingService;
    }

    @PostMapping("/request")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<ApiResponse<LinkRequestDto>> requestLink(
        Principal principal,
        @Valid @RequestBody LinkRequestCreateRequest request
    ) {
        LinkRequestDto response = linkingService.requestLink(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Solicitud de vinculacion enviada", response));
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<ApiResponse<List<LinkRequestDto>>> myRequests(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Solicitudes de vinculacion", linkingService.myRequests(principal.getName())));
    }

    @GetMapping("/my-patients")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<ApiResponse<List<LinkedPatientDto>>> myPatients(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Pacientes vinculados", linkingService.myPatients(principal.getName())));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<PendingLinkRequestDto>>> pendingRequests() {
        return ResponseEntity.ok(ApiResponse.success("Solicitudes pendientes", linkingService.pendingRequests()));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<LinkHistoryItemDto>>> history() {
        return ResponseEntity.ok(ApiResponse.success("Historial de vinculaciones", linkingService.history()));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<PendingLinkRequestDto>> approve(Principal principal, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Solicitud aprobada", linkingService.approve(principal.getName(), id)));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<PendingLinkRequestDto>> reject(
        Principal principal,
        @PathVariable Long id,
        @Valid @RequestBody LinkDecisionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Solicitud rechazada", linkingService.reject(principal.getName(), id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TUTOR', 'STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<LinkRequestDto>> revoke(
        Principal principal,
        @PathVariable Long id,
        @Valid @RequestBody LinkDecisionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Vinculacion revocada", linkingService.revoke(principal.getName(), id, request)));
    }
}
