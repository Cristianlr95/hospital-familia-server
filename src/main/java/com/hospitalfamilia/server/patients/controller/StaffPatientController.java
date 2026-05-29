package com.hospitalfamilia.server.patients.controller;

import com.hospitalfamilia.server.common.dto.ApiResponse;
import com.hospitalfamilia.server.patients.dto.StaffPatientCreateRequest;
import com.hospitalfamilia.server.patients.dto.StaffPatientDto;
import com.hospitalfamilia.server.patients.service.StaffPatientService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/patients")
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
public class StaffPatientController {

    private final StaffPatientService staffPatientService;

    public StaffPatientController(StaffPatientService staffPatientService) {
        this.staffPatientService = staffPatientService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StaffPatientDto>>> activePatients() {
        return ResponseEntity.ok(ApiResponse.success("Pacientes activos", staffPatientService.activePatients()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StaffPatientDto>> createPatient(
        @Valid @RequestBody StaffPatientCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            "Paciente creado para vinculacion familiar",
            staffPatientService.createPatient(request)
        ));
    }

    @PatchMapping("/{publicId}/deactivate")
    public ResponseEntity<ApiResponse<StaffPatientDto>> deactivatePatient(@PathVariable UUID publicId) {
        return ResponseEntity.ok(ApiResponse.success(
            "Paciente archivado y codigo de vinculacion desactivado",
            staffPatientService.deactivatePatient(publicId)
        ));
    }
}
