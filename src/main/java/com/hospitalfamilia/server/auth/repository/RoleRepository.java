package com.hospitalfamilia.server.auth.repository;

import com.hospitalfamilia.server.auth.entity.Role;
import com.hospitalfamilia.server.auth.entity.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleName name);
}
