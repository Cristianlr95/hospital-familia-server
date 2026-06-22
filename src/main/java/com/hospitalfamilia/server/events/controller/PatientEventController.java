package com.hospitalfamilia.server.events.controller;

import com.hospitalfamilia.server.common.dto.ApiResponse;
import com.hospitalfamilia.server.events.dto.PatientEventCreateRequest;
import com.hospitalfamilia.server.events.dto.PatientEventDto;
import com.hospitalfamilia.server.events.dto.PatientEventStatusUpdateRequest;
import com.hospitalfamilia.server.events.dto.PatientEventUpdateRequest;
import com.hospitalfamilia.server.events.service.PatientEventService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PatientEventController {

    private final PatientEventService eventService;

    public PatientEventController(PatientEventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/patients/{patientPublicId}/events")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<ApiResponse<List<PatientEventDto>>> upcomingEventsForTutor(
        Principal principal,
        @PathVariable UUID patientPublicId,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Eventos visibles del paciente",
            eventService.eventsForTutor(principal.getName(), patientPublicId, from, to)
        ));
    }

    @GetMapping("/events/patient/{patientPublicId}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<PatientEventDto>>> eventsForStaff(
        @PathVariable UUID patientPublicId,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Eventos del paciente",
            eventService.eventsForStaff(patientPublicId, from, to)
        ));
    }

    @PostMapping("/events")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<PatientEventDto>> createEvent(
        Principal principal,
        @Valid @RequestBody PatientEventCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Evento creado", eventService.createEvent(principal.getName(), request)));
    }

    @PutMapping("/events/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<PatientEventDto>> updateEvent(
        @PathVariable Long id,
        @Valid @RequestBody PatientEventUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Evento actualizado", eventService.updateEvent(id, request)));
    }

    @PutMapping("/events/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<PatientEventDto>> changeStatus(
        @PathVariable Long id,
        @Valid @RequestBody PatientEventStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Estado de evento actualizado", eventService.changeStatus(id, request)));
    }

    @DeleteMapping("/events/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<PatientEventDto>> cancelEvent(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Evento cancelado", eventService.cancelEvent(id)));
    }
}
