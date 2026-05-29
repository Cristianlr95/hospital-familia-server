package com.hospitalfamilia.server.linking.repository;

import com.hospitalfamilia.server.linking.entity.Patient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByLinkCodeIgnoreCaseAndActiveTrue(String linkCode);

    Optional<Patient> findByPublicId(UUID publicId);

    Optional<Patient> findByPublicIdAndActiveTrue(UUID publicId);

    boolean existsByLinkCodeIgnoreCase(String linkCode);

    List<Patient> findByActiveTrueOrderByCreatedAtDesc();
}
