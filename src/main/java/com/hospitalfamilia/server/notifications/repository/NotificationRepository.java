package com.hospitalfamilia.server.notifications.repository;

import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.notifications.entity.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop30ByRecipientOrderByCreatedAtDesc(User recipient);

    long countByReadAtIsNull();

    Optional<Notification> findByIdAndRecipient(Long id, User recipient);
}
