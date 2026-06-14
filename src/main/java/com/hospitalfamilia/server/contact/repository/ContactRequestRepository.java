package com.hospitalfamilia.server.contact.repository;

import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.contact.entity.ContactRequest;
import com.hospitalfamilia.server.contact.entity.ContactRequestStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRequestRepository extends JpaRepository<ContactRequest, Long> {
    List<ContactRequest> findByTutorOrderByCreatedAtDesc(User tutor);

    List<ContactRequest> findByStatusOrderByCreatedAtDesc(ContactRequestStatus status);
}
