package com.hospitalfamilia.server.linking.repository;

import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.linking.entity.LinkStatus;
import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.linking.entity.TutorPatientLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TutorPatientLinkRepository extends JpaRepository<TutorPatientLink, Long> {
    boolean existsByTutorAndPatient(User tutor, Patient patient);

    List<TutorPatientLink> findByTutorOrderByRequestedAtDesc(User tutor);

    List<TutorPatientLink> findByTutorAndStatusOrderByRequestedAtDesc(User tutor, LinkStatus status);

    List<TutorPatientLink> findByStatusOrderByRequestedAtAsc(LinkStatus status);

    Optional<TutorPatientLink> findByIdAndTutor(Long id, User tutor);

    Optional<TutorPatientLink> findByTutorAndPatientPublicIdAndStatus(User tutor, UUID patientPublicId, LinkStatus status);
}
