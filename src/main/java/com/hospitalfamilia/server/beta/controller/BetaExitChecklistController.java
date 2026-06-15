package com.hospitalfamilia.server.beta.controller;

import com.hospitalfamilia.server.beta.dto.BetaExitCheckUpdateRequest;
import com.hospitalfamilia.server.beta.dto.BetaExitChecklistDto;
import com.hospitalfamilia.server.beta.service.BetaExitChecklistService;
import com.hospitalfamilia.server.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/beta-exit-checklist")
public class BetaExitChecklistController {

    private final BetaExitChecklistService checklistService;

    public BetaExitChecklistController(BetaExitChecklistService checklistService) {
        this.checklistService = checklistService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<BetaExitChecklistDto>> currentChecklist() {
        return ResponseEntity.ok(ApiResponse.success("Checklist salida beta", checklistService.currentChecklist()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<BetaExitChecklistDto>> updateCheck(
        Principal principal,
        @PathVariable Long id,
        @Valid @RequestBody BetaExitCheckUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Check salida beta actualizado",
            checklistService.updateCheck(principal.getName(), id, request)
        ));
    }
}
