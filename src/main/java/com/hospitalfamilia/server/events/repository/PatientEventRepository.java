package com.hospitalfamilia.server.events.repository;

import com.hospitalfamilia.server.events.entity.PatientEvent;
import com.hospitalfamilia.server.linking.entity.Patient;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientEventRepository extends JpaRepository<PatientEvent, Long> {
    List<PatientEvent> findByPatientAndScheduledAtBetweenOrderByScheduledAtAsc(Patient patient, Instant from, Instant to);

    List<PatientEvent> findByPatientInOrderByUpdatedAtDesc(Collection<Patient> patients);

    List<PatientEvent> findTop20ByOrderByUpdatedAtDesc();
}
