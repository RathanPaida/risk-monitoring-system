package com.rathan.riskmonitor.audit;

import com.rathan.riskmonitor.alert.Alert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog recordAction(Alert alert, AuditAction action, String actor, String oldStatus, String newStatus, String details) {
        AuditLog entry = new AuditLog(alert, action, actor, oldStatus, newStatus, details);
        return auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditTrail(Long alertId) {
        return auditLogRepository.findByAlertIdOrderByCreatedAtAsc(alertId);
    }
}
