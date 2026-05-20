package com.hospitalfamilia.server.patientstatus.controller;

import com.hospitalfamilia.server.common.dto.ApiResponse;
import com.hospitalfamilia.server.patientstatus.dto.PatientStatusDto;
import com.hospitalfamilia.server.patientstatus.dto.PatientStatusUpdateRequest;
import com.hospitalfamilia.server.patientstatus.service.PatientStatusService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
public class PatientStatusController {

    private final PatientStatusService patientStatusService;

    public PatientStatusController(PatientStatusService patientStatusService) {
        this.patientStatusService = patientStatusService;
    }

    @GetMapping("/my-statuses")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<ApiResponse<List<PatientStatusDto>>> myPatientStatuses(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Estados visibles de pacientes", patientStatusService.myPatientStatuses(principal.getName())));
    }

    @GetMapping("/{patientPublicId}/status")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<ApiResponse<PatientStatusDto>> patientStatus(Principal principal, @PathVariable UUID patientPublicId) {
        return ResponseEntity.ok(ApiResponse.success("Estado visible de paciente", patientStatusService.patientStatus(principal.getName(), patientPublicId)));
    }

    @PutMapping("/{patientPublicId}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<PatientStatusDto>> updatePatientStatus(
        @PathVariable UUID patientPublicId,
        @Valid @RequestBody PatientStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Estado visible de paciente actualizado", patientStatusService.updatePatientStatus(patientPublicId, request)));
    }
}
