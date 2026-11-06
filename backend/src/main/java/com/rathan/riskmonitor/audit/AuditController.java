package com.rathan.riskmonitor.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit", description = "Immutable audit log operations (Manager only)")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Get audit trail for an alert", description = "Restricted to MANAGER role only.")
    public ResponseEntity<List<AuditDto>> getAuditLog(@RequestParam Long alertId) {
        List<AuditDto> logs = auditService.getAuditTrail(alertId)
                .stream()
                .map(AuditDto::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(logs);
    }
}
