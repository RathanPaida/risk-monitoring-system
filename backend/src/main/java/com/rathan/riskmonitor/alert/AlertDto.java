package com.rathan.riskmonitor.alert;

import java.time.Instant;

public class AlertDto {
    private Long id;
    private Long txnId;
    private String ruleName;
    private String severity;
    private String status;
    private String assignedToUsername;
    private String reason;
    private Instant createdAt;
    private Instant updatedAt;

    public AlertDto() {
    }

    public AlertDto(Alert alert) {
        this.id = alert.getId();
        this.txnId = alert.getTxnId();
        this.ruleName = alert.getRuleName();
        this.severity = alert.getSeverity().name();
        this.status = alert.getStatus().name();
        this.assignedToUsername = alert.getAssignedTo() != null ? alert.getAssignedTo().getUsername() : null;
        this.reason = alert.getReason();
        this.createdAt = alert.getCreatedAt();
        this.updatedAt = alert.getUpdatedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTxnId() {
        return txnId;
    }

    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAssignedToUsername() {
        return assignedToUsername;
    }

    public void setAssignedToUsername(String assignedToUsername) {
        this.assignedToUsername = assignedToUsername;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
