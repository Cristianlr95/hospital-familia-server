package com.hospitalfamilia.server.patientstatus.service;

import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.linking.entity.LinkStatus;
import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.linking.entity.TutorPatientLink;
import com.hospitalfamilia.server.linking.repository.TutorPatientLinkRepository;
import com.hospitalfamilia.server.patientstatus.dto.PatientStatusDto;
import com.hospitalfamilia.server.patientstatus.entity.PatientCareSnapshot;
import com.hospitalfamilia.server.patientstatus.exception.PatientStatusException;
import com.hospitalfamilia.server.patientstatus.repository.PatientCareSnapshotRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientStatusService {

    private final UserRepository userRepository;
    private final TutorPatientLinkRepository linkRepository;
    private final PatientCareSnapshotRepository snapshotRepository;

    public PatientStatusService(
        UserRepository userRepository,
        TutorPatientLinkRepository linkRepository,
        PatientCareSnapshotRepository snapshotRepository
    ) {
        this.userRepository = userRepository;
        this.linkRepository = linkRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Transactional(readOnly = true)
    public List<PatientStatusDto> myPatientStatuses(String tutorEmail) {
        User tutor = findUser(tutorEmail);
        List<TutorPatientLink> approvedLinks = linkRepository.findByTutorAndStatusOrderByRequestedAtDesc(tutor, LinkStatus.APPROVED);
        List<Patient> patients = approvedLinks.stream().map(TutorPatientLink::getPatient).toList();
        if (patients.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, PatientCareSnapshot> snapshotsByPatientId = snapshotRepository.findByPatientIn(patients).stream()
            .collect(Collectors.toMap(snapshot -> snapshot.getPatient().getId(), Function.identity()));

        return approvedLinks.stream()
            .map(TutorPatientLink::getPatient)
            .map(patient -> toDto(patient, snapshotsByPatientId.get(patient.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public PatientStatusDto patientStatus(String tutorEmail, UUID patientPublicId) {
        User tutor = findUser(tutorEmail);
        TutorPatientLink link = linkRepository.findByTutorAndPatientPublicIdAndStatus(tutor, patientPublicId, LinkStatus.APPROVED)
            .orElseThrow(() -> new PatientStatusException("Paciente no vinculado o sin autorizacion aprobada"));
        Patient patient = link.getPatient();
        PatientCareSnapshot snapshot = snapshotRepository.findByPatient(patient).orElse(null);
        return toDto(patient, snapshot);
    }

    private User findUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new PatientStatusException("Usuario no encontrado"));
    }

    private PatientStatusDto toDto(Patient patient, PatientCareSnapshot snapshot) {
        if (snapshot == null) {
            return new PatientStatusDto(
                patient.getPublicId(),
                patient.getDisplayName(),
                "Sin actualizacion disponible",
                null,
                null,
                null,
                null
            );
        }

        return new PatientStatusDto(
            patient.getPublicId(),
            patient.getDisplayName(),
            snapshot.getCareStatus(),
            snapshot.getCurrentService(),
            snapshot.getCurrentLocation(),
            snapshot.getSummary(),
            snapshot.getUpdatedAt()
        );
    }
}
