package com.rathan.riskmonitor.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public class TransactionRequest {

    private Long id;

    @NotNull(message = "accountId is required")
    private Long accountId;

    @NotNull(message = "merchantId is required")
    private Long merchantId;

    @NotNull(message = "deviceId is required")
    private Long deviceId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    private String currency;

    @NotBlank(message = "country is required")
    private String country;

    @NotNull(message = "txnTime is required")
    private Instant txnTime;

    @NotBlank(message = "channel is required")
    private String channel;

    public TransactionRequest() {
    }

    public TransactionRequest(Long id, Long accountId, Long merchantId, Long deviceId, BigDecimal amount, String currency, String country, Instant txnTime, String channel) {
        this.id = id;
        this.accountId = accountId;
        this.merchantId = merchantId;
        this.deviceId = deviceId;
        this.amount = amount;
        this.currency = currency;
        this.country = country;
        this.txnTime = txnTime;
        this.channel = channel;
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
}
