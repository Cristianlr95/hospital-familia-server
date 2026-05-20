package com.hospitalfamilia.server.patientstatus.repository;

import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.patientstatus.entity.PatientCareSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientCareSnapshotRepository extends JpaRepository<PatientCareSnapshot, Long> {
    List<PatientCareSnapshot> findByPatientIn(Collection<Patient> patients);

    Optional<PatientCareSnapshot> findByPatient(Patient patient);

    List<PatientCareSnapshot> findTop20ByOrderByUpdatedAtDesc();
}
