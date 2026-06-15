package com.hospitalfamilia.server.beta.service;

import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.beta.dto.BetaExitCheckDto;
import com.hospitalfamilia.server.beta.dto.BetaExitCheckUpdateRequest;
import com.hospitalfamilia.server.beta.dto.BetaExitChecklistDto;
import com.hospitalfamilia.server.beta.entity.BetaExitCheck;
import com.hospitalfamilia.server.beta.exception.BetaExitChecklistException;
import com.hospitalfamilia.server.beta.repository.BetaExitCheckRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BetaExitChecklistService {

    private final BetaExitCheckRepository checkRepository;
    private final UserRepository userRepository;

    public BetaExitChecklistService(BetaExitCheckRepository checkRepository, UserRepository userRepository) {
        this.checkRepository = checkRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public BetaExitChecklistDto currentChecklist() {
        List<BetaExitCheckDto> checks = checkRepository.findAllByOrderBySortOrderAsc().stream()
            .map(this::toDto)
            .toList();
        return toChecklist(checks);
    }

    @Transactional
    public BetaExitChecklistDto updateCheck(String staffEmail, Long checkId, BetaExitCheckUpdateRequest request) {
        User staff = userRepository.findByEmailIgnoreCase(staffEmail)
            .orElseThrow(() -> new BetaExitChecklistException("Usuario staff no encontrado"));
        BetaExitCheck check = checkRepository.findById(checkId)
            .orElseThrow(() -> new BetaExitChecklistException("Check beta no encontrado"));

        check.update(request.completed(), cleanOptional(request.notes()), staff);
        checkRepository.save(check);
        return currentChecklist();
    }

    private BetaExitChecklistDto toChecklist(List<BetaExitCheckDto> checks) {
        int total = checks.size();
        int completed = (int) checks.stream().filter(BetaExitCheckDto::completed).count();
        int progress = total == 0 ? 0 : Math.round((completed * 100.0f) / total);
        return new BetaExitChecklistDto(Instant.now(), completed, total, progress, checks);
    }

    private BetaExitCheckDto toDto(BetaExitCheck check) {
        User completedBy = check.getCompletedBy();
        return new BetaExitCheckDto(
            check.getId(),
            check.getKey(),
            check.getLabel(),
            check.getDescription(),
            check.getSortOrder(),
            check.isCompleted(),
            check.getNotes(),
            completedBy == null ? null : completedBy.getFirstName() + " " + completedBy.getLastName(),
            check.getCompletedAt(),
            check.getUpdatedAt()
        );
    }

    private String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
