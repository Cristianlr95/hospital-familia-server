package com.hospitalfamilia.server.notifications.repository;

import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.notifications.entity.NotificationPreference;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    Optional<NotificationPreference> findByUser(User user);
}
