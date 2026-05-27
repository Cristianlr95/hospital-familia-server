package com.hospitalfamilia.server.patients.service;

import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.linking.exception.LinkingException;
import com.hospitalfamilia.server.linking.repository.PatientRepository;
import com.hospitalfamilia.server.patients.dto.StaffPatientCreateRequest;
import com.hospitalfamilia.server.patients.dto.StaffPatientDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffPatientService {

    private final PatientRepository patientRepository;

    public StaffPatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Transactional(readOnly = true)
    public List<StaffPatientDto> activePatients() {
        return patientRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public StaffPatientDto createPatient(StaffPatientCreateRequest request) {
        String linkCode = normalizeCode(request.linkCode());
        if (patientRepository.existsByLinkCodeIgnoreCase(linkCode)) {
            throw new LinkingException("Ya existe un paciente con este codigo de vinculacion");
        }

        Patient patient = patientRepository.save(new Patient(linkCode, request.displayName().trim()));
        return toDto(patient);
    }

    private StaffPatientDto toDto(Patient patient) {
        return new StaffPatientDto(
            patient.getPublicId(),
            patient.getDisplayName(),
            patient.getLinkCode(),
            patient.isActive(),
            patient.getCreatedAt(),
            patient.getUpdatedAt()
        );
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }
}
