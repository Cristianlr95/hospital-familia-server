package com.hospitalfamilia.server.contact.controller;

import com.hospitalfamilia.server.common.dto.ApiResponse;
import com.hospitalfamilia.server.contact.dto.ContactRequestCreateRequest;
import com.hospitalfamilia.server.contact.dto.ContactRequestDto;
import com.hospitalfamilia.server.contact.dto.ContactRequestResolveRequest;
import com.hospitalfamilia.server.contact.service.ContactRequestService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact-requests")
public class ContactRequestController {

    private final ContactRequestService contactRequestService;

    public ContactRequestController(ContactRequestService contactRequestService) {
        this.contactRequestService = contactRequestService;
    }

    @PostMapping
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<ApiResponse<ContactRequestDto>> create(
        Principal principal,
        @Valid @RequestBody ContactRequestCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Solicitud de contacto enviada", contactRequestService.create(principal.getName(), request)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<ApiResponse<List<ContactRequestDto>>> myRequests(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(
            "Solicitudes de contacto del tutor",
            contactRequestService.myRequests(principal.getName())
        ));
    }

    @GetMapping("/staff/open")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ContactRequestDto>>> openRequestsForStaff() {
        return ResponseEntity.ok(ApiResponse.success(
            "Solicitudes de contacto abiertas",
            contactRequestService.openRequestsForStaff()
        ));
    }

    @PutMapping("/staff/{id}/resolve")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ContactRequestDto>> resolve(
        Principal principal,
        @PathVariable Long id,
        @Valid @RequestBody ContactRequestResolveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Solicitud de contacto resuelta",
            contactRequestService.resolve(principal.getName(), id, request)
        ));
    }
}
