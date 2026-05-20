package com.hospitalfamilia.server.activity.service;

import com.hospitalfamilia.server.activity.dto.ActivityFeedItemDto;
import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.events.entity.PatientEvent;
import com.hospitalfamilia.server.events.repository.PatientEventRepository;
import com.hospitalfamilia.server.linking.entity.LinkStatus;
import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.linking.entity.TutorPatientLink;
import com.hospitalfamilia.server.linking.exception.LinkingException;
import com.hospitalfamilia.server.linking.repository.TutorPatientLinkRepository;
import com.hospitalfamilia.server.patientstatus.entity.PatientCareSnapshot;
import com.hospitalfamilia.server.patientstatus.repository.PatientCareSnapshotRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityFeedService {

    private static final int MAX_ITEMS = 20;

    private final UserRepository userRepository;
    private final TutorPatientLinkRepository linkRepository;
    private final PatientCareSnapshotRepository snapshotRepository;
    private final PatientEventRepository eventRepository;

    public ActivityFeedService(
        UserRepository userRepository,
        TutorPatientLinkRepository linkRepository,
        PatientCareSnapshotRepository snapshotRepository,
        PatientEventRepository eventRepository
    ) {
        this.userRepository = userRepository;
        this.linkRepository = linkRepository;
        this.snapshotRepository = snapshotRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public List<ActivityFeedItemDto> tutorFeed(String tutorEmail) {
        User tutor = userRepository.findByEmailIgnoreCase(tutorEmail)
            .orElseThrow(() -> new LinkingException("Usuario no encontrado"));

        List<TutorPatientLink> requests = linkRepository.findByTutorOrderByRequestedAtDesc(tutor);
        List<TutorPatientLink> approvedLinks = requests.stream()
            .filter(link -> link.getStatus() == LinkStatus.APPROVED)
            .toList();
        List<Patient> approvedPatients = approvedLinks.stream().map(TutorPatientLink::getPatient).toList();

        List<ActivityFeedItemDto> items = new ArrayList<>();
        requests.forEach(link -> items.add(toTutorLinkItem(link)));

        if (!approvedPatients.isEmpty()) {
            Map<Long, PatientCareSnapshot> snapshotsByPatientId = snapshotRepository.findByPatientIn(approvedPatients).stream()
                .collect(Collectors.toMap(snapshot -> snapshot.getPatient().getId(), Function.identity()));
            approvedPatients.stream()
                .map(patient -> snapshotsByPatientId.get(patient.getId()))
                .filter(snapshot -> snapshot != null)
                .forEach(snapshot -> items.add(toSnapshotItem("TUTOR", snapshot)));

            eventRepository.findByPatientInOrderByUpdatedAtDesc(approvedPatients).stream()
                .limit(MAX_ITEMS)
                .map(event -> toEventItem("TUTOR", event))
                .forEach(items::add);
        }

        return sortAndLimit(items);
    }

    @Transactional(readOnly = true)
    public List<ActivityFeedItemDto> staffFeed() {
        List<ActivityFeedItemDto> items = new ArrayList<>();

        linkRepository.findByStatusOrderByRequestedAtAsc(LinkStatus.PENDING).stream()
            .map(this::toPendingItem)
            .forEach(items::add);

        linkRepository.findByStatusNotOrderByDecidedAtDescRequestedAtDesc(LinkStatus.PENDING).stream()
            .limit(MAX_ITEMS)
            .map(this::toStaffLinkHistoryItem)
            .forEach(items::add);

        eventRepository.findTop20ByOrderByUpdatedAtDesc().stream()
            .map(event -> toEventItem("STAFF", event))
            .forEach(items::add);

        snapshotRepository.findTop20ByOrderByUpdatedAtDesc().stream()
            .map(snapshot -> toSnapshotItem("STAFF", snapshot))
            .forEach(items::add);

        return sortAndLimit(items);
    }

    private List<ActivityFeedItemDto> sortAndLimit(List<ActivityFeedItemDto> items) {
        return items.stream()
            .sorted(Comparator.comparing(ActivityFeedItemDto::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(MAX_ITEMS)
            .toList();
    }

    private ActivityFeedItemDto toTutorLinkItem(TutorPatientLink link) {
        Patient patient = link.getPatient();
        Instant occurredAt = link.getDecidedAt() != null ? link.getDecidedAt() : link.getRequestedAt();

        return new ActivityFeedItemDto(
            "TUTOR",
            "LINK",
            occurredAt,
            patient.getPublicId(),
            patient.getDisplayName(),
            linkTitle(link.getStatus()),
            linkMessageForTutor(link),
            link.getStatus().name(),
            null
        );
    }

    private ActivityFeedItemDto toPendingItem(TutorPatientLink link) {
        Patient patient = link.getPatient();
        User tutor = link.getTutor();

        return new ActivityFeedItemDto(
            "STAFF",
            "LINK_PENDING",
            link.getRequestedAt(),
            patient.getPublicId(),
            patient.getDisplayName(),
            "Solicitud pendiente por revisar",
            tutor.getFirstName() + " " + tutor.getLastName() + " solicito acceso para familia.",
            link.getStatus().name(),
            tutor.getFirstName() + " " + tutor.getLastName()
        );
    }

    private ActivityFeedItemDto toStaffLinkHistoryItem(TutorPatientLink link) {
        Patient patient = link.getPatient();
        User tutor = link.getTutor();
        String actorName = link.getDecidedBy() == null ? null : link.getDecidedBy().getFirstName() + " " + link.getDecidedBy().getLastName();

        return new ActivityFeedItemDto(
            "STAFF",
            "LINK_HISTORY",
            link.getDecidedAt() != null ? link.getDecidedAt() : link.getRequestedAt(),
            patient.getPublicId(),
            patient.getDisplayName(),
            linkTitle(link.getStatus()),
            tutor.getFirstName() + " " + tutor.getLastName() + " - " + linkMessageForStaff(link),
            link.getStatus().name(),
            actorName
        );
    }

    private ActivityFeedItemDto toEventItem(String audience, PatientEvent event) {
        Patient patient = event.getPatient();

        return new ActivityFeedItemDto(
            audience,
            "EVENT",
            event.getUpdatedAt(),
            patient.getPublicId(),
            patient.getDisplayName(),
            event.getTitle(),
            eventMessage(event),
            event.getStatus().name(),
            event.getResponsibleStaff()
        );
    }

    private ActivityFeedItemDto toSnapshotItem(String audience, PatientCareSnapshot snapshot) {
        Patient patient = snapshot.getPatient();
        String service = snapshot.getCurrentService() == null ? "servicio no informado" : snapshot.getCurrentService();

        return new ActivityFeedItemDto(
            audience,
            "STATUS",
            snapshot.getUpdatedAt(),
            patient.getPublicId(),
            patient.getDisplayName(),
            "Estado del paciente actualizado",
            snapshot.getCareStatus() + " en " + service,
            snapshot.getCareStatus(),
            null
        );
    }

    private String linkTitle(LinkStatus status) {
        return switch (status) {
            case PENDING -> "Solicitud enviada";
            case APPROVED -> "Vinculacion aprobada";
            case REJECTED -> "Vinculacion rechazada";
            case REVOKED -> "Vinculacion revocada";
        };
    }

    private String linkMessageForTutor(TutorPatientLink link) {
        return switch (link.getStatus()) {
            case PENDING -> "Tu solicitud esta en revision por el hospital.";
            case APPROVED -> "Ya puedes ver el estado autorizado del paciente.";
            case REJECTED -> link.getDecisionReason() == null
                ? "La solicitud fue rechazada por el hospital."
                : "La solicitud fue rechazada: " + link.getDecisionReason();
            case REVOKED -> link.getDecisionReason() == null
                ? "La vinculacion fue revocada."
                : "La vinculacion fue revocada: " + link.getDecisionReason();
        };
    }

    private String linkMessageForStaff(TutorPatientLink link) {
        return switch (link.getStatus()) {
            case APPROVED -> "acceso aprobado";
            case REJECTED -> link.getDecisionReason() == null
                ? "solicitud rechazada"
                : "solicitud rechazada: " + link.getDecisionReason();
            case REVOKED -> link.getDecisionReason() == null
                ? "vinculacion revocada"
                : "vinculacion revocada: " + link.getDecisionReason();
            case PENDING -> "solicitud pendiente";
        };
    }

    private String eventMessage(PatientEvent event) {
        String when = event.getScheduledAt().truncatedTo(ChronoUnit.MINUTES).toString();
        return event.getStatus().name() + " - " + when;
    }
}
