package com.rathan.riskmonitor.alert;

import jakarta.validation.constraints.NotBlank;

public class EscalateRequest {

    @NotBlank(message = "reason is required")
    private String reason;

    public EscalateRequest() {
    }

    public EscalateRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
