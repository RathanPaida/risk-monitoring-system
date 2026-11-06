package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.alert.AlertSeverity;

public class RuleHit {
    private final String ruleName;
    private final AlertSeverity severity;
    private final String reason;

    public RuleHit(String ruleName, AlertSeverity severity, String reason) {
        this.ruleName = ruleName;
        this.severity = severity;
        this.reason = reason;
    }

    public String getRuleName() {
        return ruleName;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public String getReason() {
        return reason;
    }
}
