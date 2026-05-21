package com.hospitalfamilia.server.linking.service;

import com.hospitalfamilia.server.auth.entity.RoleName;
import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.linking.dto.LinkDecisionRequest;
import com.hospitalfamilia.server.linking.dto.LinkHistoryItemDto;
import com.hospitalfamilia.server.linking.dto.LinkRequestCreateRequest;
import com.hospitalfamilia.server.linking.dto.LinkRequestDto;
import com.hospitalfamilia.server.linking.dto.LinkedPatientDto;
import com.hospitalfamilia.server.linking.dto.PendingLinkRequestDto;
import com.hospitalfamilia.server.linking.entity.LinkStatus;
import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.linking.entity.TutorPatientLink;
import com.hospitalfamilia.server.linking.exception.LinkingException;
import com.hospitalfamilia.server.linking.repository.PatientRepository;
import com.hospitalfamilia.server.linking.repository.TutorPatientLinkRepository;
import com.hospitalfamilia.server.notifications.entity.NotificationType;
import com.hospitalfamilia.server.notifications.service.NotificationCenterService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkingService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final TutorPatientLinkRepository linkRepository;
    private final NotificationCenterService notificationCenterService;

    public LinkingService(
        UserRepository userRepository,
        PatientRepository patientRepository,
        TutorPatientLinkRepository linkRepository,
        NotificationCenterService notificationCenterService
    ) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.linkRepository = linkRepository;
        this.notificationCenterService = notificationCenterService;
    }

    @Transactional
    public LinkRequestDto requestLink(String tutorEmail, LinkRequestCreateRequest request) {
        User tutor = findUser(tutorEmail);
        Patient patient = patientRepository.findByLinkCodeIgnoreCaseAndActiveTrue(normalizeCode(request.patientCode()))
            .orElseThrow(() -> new LinkingException("Codigo de paciente invalido o no disponible"));

        if (linkRepository.existsByTutorAndPatient(tutor, patient)) {
            throw new LinkingException("Ya existe una solicitud para este paciente");
        }

        TutorPatientLink link = linkRepository.save(new TutorPatientLink(tutor, patient));
        return toTutorDto(link);
    }

    @Transactional(readOnly = true)
    public List<LinkRequestDto> myRequests(String tutorEmail) {
        User tutor = findUser(tutorEmail);
        return linkRepository.findByTutorOrderByRequestedAtDesc(tutor).stream()
            .map(this::toTutorDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<LinkedPatientDto> myPatients(String tutorEmail) {
        User tutor = findUser(tutorEmail);
        return linkRepository.findByTutorAndStatusOrderByRequestedAtDesc(tutor, LinkStatus.APPROVED).stream()
            .map(link -> new LinkedPatientDto(link.getPatient().getPublicId(), link.getPatient().getDisplayName()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PendingLinkRequestDto> pendingRequests() {
        return linkRepository.findByStatusOrderByRequestedAtAsc(LinkStatus.PENDING).stream()
            .map(this::toPendingDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<LinkHistoryItemDto> history() {
        return linkRepository.findByStatusNotOrderByDecidedAtDescRequestedAtDesc(LinkStatus.PENDING).stream()
            .map(this::toHistoryDto)
            .toList();
    }

    @Transactional
    public PendingLinkRequestDto approve(String staffEmail, Long linkId) {
        User staff = findUser(staffEmail);
        TutorPatientLink link = findPendingLink(linkId);
        link.approve(staff);
        TutorPatientLink savedLink = linkRepository.save(link);
        notificationCenterService.notifyTutor(
            savedLink.getTutor(),
            savedLink.getPatient(),
            NotificationType.LINKING_APPROVED,
            "Vinculacion aprobada",
            "Ya puedes revisar el estado autorizado de " + savedLink.getPatient().getDisplayName()
        );
        return toPendingDto(savedLink);
    }

    @Transactional
    public PendingLinkRequestDto reject(String staffEmail, Long linkId, LinkDecisionRequest request) {
        User staff = findUser(staffEmail);
        TutorPatientLink link = findPendingLink(linkId);
        link.reject(staff, cleanReason(request.reason()));
        TutorPatientLink savedLink = linkRepository.save(link);
        notificationCenterService.notifyTutor(
            savedLink.getTutor(),
            savedLink.getPatient(),
            NotificationType.LINKING_REJECTED,
            "Vinculacion rechazada",
            savedLink.getDecisionReason() == null ? "La solicitud fue rechazada por el hospital." : savedLink.getDecisionReason()
        );
        return toPendingDto(savedLink);
    }

    @Transactional
    public LinkRequestDto revoke(String actorEmail, Long linkId, LinkDecisionRequest request) {
        User actor = findUser(actorEmail);
        TutorPatientLink link = linkRepository.findById(linkId)
            .orElseThrow(() -> new LinkingException("Solicitud de vinculacion no encontrada"));

        if (!isStaff(actor) && !link.getTutor().getId().equals(actor.getId())) {
            throw new LinkingException("No tienes permisos para revocar esta vinculacion");
        }

        link.revoke(actor, cleanReason(request.reason()));
        TutorPatientLink savedLink = linkRepository.save(link);
        notificationCenterService.notifyTutor(
            savedLink.getTutor(),
            savedLink.getPatient(),
            NotificationType.LINKING_REVOKED,
            "Vinculacion revocada",
            savedLink.getDecisionReason() == null ? "El acceso familiar fue revocado." : savedLink.getDecisionReason()
        );
        return toTutorDto(savedLink);
    }

    private TutorPatientLink findPendingLink(Long linkId) {
        TutorPatientLink link = linkRepository.findById(linkId)
            .orElseThrow(() -> new LinkingException("Solicitud de vinculacion no encontrada"));
        if (link.getStatus() != LinkStatus.PENDING) {
            throw new LinkingException("Solo se pueden decidir solicitudes pendientes");
        }
        return link;
    }

    private User findUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new LinkingException("Usuario no encontrado"));
    }

    private LinkRequestDto toTutorDto(TutorPatientLink link) {
        boolean canShowPatient = link.getStatus() == LinkStatus.APPROVED;
        Patient patient = link.getPatient();
        return new LinkRequestDto(
            link.getId(),
            link.getStatus(),
            link.getRequestedAt(),
            link.getDecidedAt(),
            link.getDecisionReason(),
            canShowPatient ? patient.getPublicId() : null,
            canShowPatient ? patient.getDisplayName() : null
        );
    }

    private PendingLinkRequestDto toPendingDto(TutorPatientLink link) {
        User tutor = link.getTutor();
        Patient patient = link.getPatient();
        return new PendingLinkRequestDto(
            link.getId(),
            link.getStatus(),
            link.getRequestedAt(),
            tutor.getEmail(),
            tutor.getFirstName() + " " + tutor.getLastName(),
            patient.getPublicId(),
            patient.getDisplayName()
        );
    }

    private LinkHistoryItemDto toHistoryDto(TutorPatientLink link) {
        User tutor = link.getTutor();
        Patient patient = link.getPatient();
        User decidedBy = link.getDecidedBy();
        String decidedByName = decidedBy == null ? null : decidedBy.getFirstName() + " " + decidedBy.getLastName();

        return new LinkHistoryItemDto(
            link.getId(),
            link.getStatus(),
            link.getRequestedAt(),
            link.getDecidedAt(),
            link.getDecisionReason(),
            tutor.getEmail(),
            tutor.getFirstName() + " " + tutor.getLastName(),
            patient.getPublicId(),
            patient.getDisplayName(),
            decidedBy == null ? null : decidedBy.getEmail(),
            decidedByName
        );
    }

    private boolean isStaff(User user) {
        return user.getRoles().stream()
            .anyMatch(role -> role.getName() == RoleName.STAFF || role.getName() == RoleName.ADMIN);
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private String cleanReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }
}
