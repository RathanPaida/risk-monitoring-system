package com.rathan.riskmonitor.transaction.dto;

public class BulkTransactionResponse {
    private int ingestedCount;
    private int alertsGenerated;

    public BulkTransactionResponse() {
    }

    public BulkTransactionResponse(int ingestedCount, int alertsGenerated) {
        this.ingestedCount = ingestedCount;
        this.alertsGenerated = alertsGenerated;
    }

    public int getIngestedCount() {
        return ingestedCount;
    }

    public void setIngestedCount(int ingestedCount) {
        this.ingestedCount = ingestedCount;
    }

    public int getAlertsGenerated() {
        return alertsGenerated;
    }

    public void setAlertsGenerated(int alertsGenerated) {
        this.alertsGenerated = alertsGenerated;
    }
}
