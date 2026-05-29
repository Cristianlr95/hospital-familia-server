package com.hospitalfamilia.server.auth.repository;

import com.hospitalfamilia.server.auth.entity.AuthSession;
import com.hospitalfamilia.server.auth.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findBySessionId(UUID sessionId);
    List<AuthSession> findByUserOrderByCreatedAtDesc(User user);
    List<AuthSession> findByUserAndRevokedAtIsNull(User user);
}
