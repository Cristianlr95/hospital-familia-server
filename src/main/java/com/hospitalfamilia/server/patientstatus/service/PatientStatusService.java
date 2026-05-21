package com.hospitalfamilia.server.patientstatus.service;

import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.linking.entity.LinkStatus;
import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.linking.entity.TutorPatientLink;
import com.hospitalfamilia.server.linking.repository.TutorPatientLinkRepository;
import com.hospitalfamilia.server.patientstatus.dto.PatientStatusDto;
import com.hospitalfamilia.server.patientstatus.dto.PatientStatusUpdateRequest;
import com.hospitalfamilia.server.patientstatus.entity.PatientCareSnapshot;
import com.hospitalfamilia.server.patientstatus.exception.PatientStatusException;
import com.hospitalfamilia.server.patientstatus.repository.PatientCareSnapshotRepository;
import com.hospitalfamilia.server.linking.repository.PatientRepository;
import com.hospitalfamilia.server.notifications.entity.NotificationType;
import com.hospitalfamilia.server.notifications.service.NotificationCenterService;
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
    private final PatientRepository patientRepository;
    private final PatientCareSnapshotRepository snapshotRepository;
    private final NotificationCenterService notificationCenterService;

    public PatientStatusService(
        UserRepository userRepository,
        TutorPatientLinkRepository linkRepository,
        PatientRepository patientRepository,
        PatientCareSnapshotRepository snapshotRepository,
        NotificationCenterService notificationCenterService
    ) {
        this.userRepository = userRepository;
        this.linkRepository = linkRepository;
        this.patientRepository = patientRepository;
        this.snapshotRepository = snapshotRepository;
        this.notificationCenterService = notificationCenterService;
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

    @Transactional
    public PatientStatusDto updatePatientStatus(UUID patientPublicId, PatientStatusUpdateRequest request) {
        Patient patient = patientRepository.findByPublicId(patientPublicId)
            .orElseThrow(() -> new PatientStatusException("Paciente no encontrado"));
        PatientCareSnapshot snapshot = snapshotRepository.findByPatient(patient)
            .orElseGet(() -> new PatientCareSnapshot(patient, request.careStatus().trim(), clean(request.currentService()), clean(request.currentLocation()), clean(request.summary())));

        snapshot.update(request.careStatus().trim(), clean(request.currentService()), clean(request.currentLocation()), clean(request.summary()));
        PatientCareSnapshot savedSnapshot = snapshotRepository.save(snapshot);
        notificationCenterService.notifyApprovedTutors(
            patient,
            NotificationType.STATE_CHANGE,
            "Estado actualizado",
            patient.getDisplayName() + ": " + savedSnapshot.getCareStatus()
        );
        return toDto(patient, savedSnapshot);
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

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
