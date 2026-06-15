package com.hospitalfamilia.server.beta.repository;

import com.hospitalfamilia.server.beta.entity.BetaExitCheck;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BetaExitCheckRepository extends JpaRepository<BetaExitCheck, Long> {
    List<BetaExitCheck> findAllByOrderBySortOrderAsc();
}
