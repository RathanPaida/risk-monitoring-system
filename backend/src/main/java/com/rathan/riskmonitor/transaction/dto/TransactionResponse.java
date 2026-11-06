package com.rathan.riskmonitor.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class TransactionResponse {

    private Long id;
    private Long accountId;
    private Long merchantId;
    private Long deviceId;
    private BigDecimal amount;
    private String currency;
    private String country;
    private Instant txnTime;
    private String channel;
    private Instant createdAt;
    private int alertsGenerated;
    private List<String> triggeredRules;

    public TransactionResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Instant getTxnTime() {
        return txnTime;
    }

    public void setTxnTime(Instant txnTime) {
        this.txnTime = txnTime;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public int getAlertsGenerated() {
        return alertsGenerated;
    }

    public void setAlertsGenerated(int alertsGenerated) {
        this.alertsGenerated = alertsGenerated;
    }

    public List<String> getTriggeredRules() {
        return triggeredRules;
    }

    public void setTriggeredRules(List<String> triggeredRules) {
        this.triggeredRules = triggeredRules;
    }
}
