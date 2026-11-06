package com.rathan.riskmonitor.alert;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alerts", description = "Alert triage, review, and escalation workflow operations")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER')")
    @Operation(summary = "List and filter alerts", description = "Analysts see own assigned and open unassigned alerts; Managers see all.")
    public ResponseEntity<Page<AlertDto>> listAlerts(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AlertDto> alerts = alertService.listAlerts(status, severity, userDetails.getUsername(), pageable)
                .map(AlertDto::new);
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER')")
    @Operation(summary = "Get alert details by ID")
    public ResponseEntity<AlertDto> getAlert(@PathVariable Long id) {
        Alert alert = alertService.getAlert(id);
        return ResponseEntity.ok(new AlertDto(alert));
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER')")
    @Operation(summary = "Claim an OPEN alert (OPEN -> IN_REVIEW)")
    public ResponseEntity<AlertDto> claimAlert(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Alert updated = alertService.claimAlert(id, userDetails.getUsername());
        return ResponseEntity.ok(new AlertDto(updated));
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER')")
    @Operation(summary = "Escalate an alert under review (IN_REVIEW -> ESCALATED)")
    public ResponseEntity<AlertDto> escalateAlert(
            @PathVariable Long id,
            @Valid @RequestBody EscalateRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Alert updated = alertService.escalateAlert(id, userDetails.getUsername(), request.getReason());
        return ResponseEntity.ok(new AlertDto(updated));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER')")
    @Operation(summary = "Close an alert (IN_REVIEW -> CLOSED, or ESCALATED -> CLOSED for Managers)")
    public ResponseEntity<AlertDto> closeAlert(
            @PathVariable Long id,
            @Valid @RequestBody CloseRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Alert updated = alertService.closeAlert(id, userDetails.getUsername(), request.getOutcome(), request.getComment());
        return ResponseEntity.ok(new AlertDto(updated));
    }
}
