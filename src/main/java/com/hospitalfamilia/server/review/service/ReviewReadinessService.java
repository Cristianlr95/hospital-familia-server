package com.hospitalfamilia.server.review.service;

import com.hospitalfamilia.server.events.repository.PatientEventRepository;
import com.hospitalfamilia.server.linking.entity.LinkStatus;
import com.hospitalfamilia.server.linking.repository.PatientRepository;
import com.hospitalfamilia.server.linking.repository.TutorPatientLinkRepository;
import com.hospitalfamilia.server.notifications.repository.NotificationRepository;
import com.hospitalfamilia.server.review.dto.ReviewReadinessCheckDto;
import com.hospitalfamilia.server.review.dto.ReviewReadinessDto;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReviewReadinessService {

    private final PatientRepository patientRepository;
    private final TutorPatientLinkRepository linkRepository;
    private final PatientEventRepository eventRepository;
    private final NotificationRepository notificationRepository;

    public ReviewReadinessService(
        PatientRepository patientRepository,
        TutorPatientLinkRepository linkRepository,
        PatientEventRepository eventRepository,
        NotificationRepository notificationRepository
    ) {
        this.patientRepository = patientRepository;
        this.linkRepository = linkRepository;
        this.eventRepository = eventRepository;
        this.notificationRepository = notificationRepository;
    }

    public ReviewReadinessDto currentReadiness() {
        Instant generatedAt = Instant.now();
        long activePatients = patientRepository.countByActiveTrue();
        long pendingLinks = linkRepository.countByStatus(LinkStatus.PENDING);
        long approvedLinks = linkRepository.countByStatus(LinkStatus.APPROVED);
        long upcomingEvents = eventRepository.countByScheduledAtGreaterThanEqual(generatedAt);
        long unreadNotifications = notificationRepository.countByReadAtIsNull();

        List<ReviewReadinessCheckDto> checks = List.of(
            check(
                "active-patients",
                "Pacientes activos disponibles",
                activePatients > 0,
                activePatients + " paciente(s) activo(s) para revision"
            ),
            check(
                "approved-links",
                "Familias vinculadas y aprobadas",
                approvedLinks > 0,
                approvedLinks + " vinculacion(es) aprobada(s)"
            ),
            check(
                "upcoming-events",
                "Eventos familiares visibles",
                upcomingEvents > 0,
                upcomingEvents + " evento(s) futuro(s) registrados"
            ),
            check(
                "notifications",
                "Notificaciones in-app pendientes",
                unreadNotifications > 0,
                unreadNotifications + " notificacion(es) sin leer"
            ),
            check(
                "pending-queue",
                "Cola staff medible",
                true,
                pendingLinks + " solicitud(es) pendiente(s) al corte"
            )
        );

        int passedChecks = (int) checks.stream().filter(ReviewReadinessCheckDto::passed).count();
        return new ReviewReadinessDto(
            generatedAt,
            activePatients,
            pendingLinks,
            approvedLinks,
            upcomingEvents,
            unreadNotifications,
            passedChecks,
            checks.size(),
            checks
        );
    }

    private ReviewReadinessCheckDto check(String key, String label, boolean passed, String detail) {
        return new ReviewReadinessCheckDto(key, label, passed, detail);
    }
}
