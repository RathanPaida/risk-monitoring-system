package com.rathan.riskmonitor.audit;

import java.time.Instant;

public class AuditDto {
    private Long id;
    private Long alertId;
    private String action;
    private String actor;
    private String oldStatus;
    private String newStatus;
    private String details;
    private Instant createdAt;

    public AuditDto() {
    }

    public AuditDto(AuditLog log) {
        this.id = log.getId();
        this.alertId = log.getAlertId();
        this.action = log.getAction().name();
        this.actor = log.getActor();
        this.oldStatus = log.getOldStatus();
        this.newStatus = log.getNewStatus();
        this.details = log.getDetails();
        this.createdAt = log.getCreatedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(String oldStatus) {
        this.oldStatus = oldStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
