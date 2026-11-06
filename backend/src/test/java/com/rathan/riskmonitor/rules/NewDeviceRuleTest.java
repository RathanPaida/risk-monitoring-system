package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.alert.AlertSeverity;
import com.rathan.riskmonitor.transaction.Account;
import com.rathan.riskmonitor.transaction.Device;
import com.rathan.riskmonitor.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class NewDeviceRuleTest {

    private RuleProperties properties;
    private NewDeviceRule rule;
    private RuleContext context;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        properties = new RuleProperties();
        properties.getNewDevice().setMinAmountLow(200.0);
        properties.getNewDevice().setMinAmountMedium(1000.0);

        rule = new NewDeviceRule(properties);
        context = Mockito.mock(RuleContext.class);

        Account account = new Account();
        account.setId(401L);

        Device device = new Device();
        device.setId(99L);

        transaction = new Transaction();
        transaction.setId(30L);
        transaction.setAccount(account);
        transaction.setDevice(device);
        transaction.setTxnTime(Instant.parse("2026-10-15T12:00:00Z"));
    }

    @Test
    @DisplayName("Clear Miss: Device already seen on account (priorUsage > 0)")
    void evaluate_deviceAlreadySeen_returnsEmpty() {
        transaction.setAmount(new BigDecimal("2500.00"));
        when(context.countPriorAccountDeviceUsage(eq(401L), eq(99L), any(Instant.class)))
                .thenReturn(5L);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isEmpty();
    }

    @Test
    @DisplayName("Clear Miss: Amount below minimum low threshold ($150 < $200)")
    void evaluate_newDeviceAmountBelowFloor_returnsEmpty() {
        transaction.setAmount(new BigDecimal("150.00"));
        when(context.countPriorAccountDeviceUsage(eq(401L), eq(99L), any(Instant.class)))
                .thenReturn(0L);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isEmpty();
    }

    @Test
    @DisplayName("Boundary Case: Amount just under low threshold ($199.99 < $200) -> Returns Empty")
    void evaluate_amountJustUnderLowThreshold_returnsEmpty() {
        transaction.setAmount(new BigDecimal("199.99"));
        when(context.countPriorAccountDeviceUsage(eq(401L), eq(99L), any(Instant.class)))
                .thenReturn(0L);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isEmpty();
    }

    @Test
    @DisplayName("Boundary Hit: Amount exactly at low threshold ($200.00) -> LOW")
    void evaluate_amountExactlyAtLowThreshold_returnsLowHit() {
        transaction.setAmount(new BigDecimal("200.00"));
        when(context.countPriorAccountDeviceUsage(eq(401L), eq(99L), any(Instant.class)))
                .thenReturn(0L);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isPresent();
        assertThat(hit.get().getRuleName()).isEqualTo(NewDeviceRule.NAME);
        assertThat(hit.get().getSeverity()).isEqualTo(AlertSeverity.LOW);
    }

    @Test
    @DisplayName("Boundary Case: Amount just under medium threshold ($999.99 < $1000) -> LOW")
    void evaluate_amountJustUnderMediumThreshold_returnsLowHit() {
        transaction.setAmount(new BigDecimal("999.99"));
        when(context.countPriorAccountDeviceUsage(eq(401L), eq(99L), any(Instant.class)))
                .thenReturn(0L);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isPresent();
        assertThat(hit.get().getSeverity()).isEqualTo(AlertSeverity.LOW);
    }

    @Test
    @DisplayName("Boundary Hit: Amount exactly at medium threshold ($1000.00) -> MEDIUM")
    void evaluate_amountExactlyAtMediumThreshold_returnsMediumHit() {
        transaction.setAmount(new BigDecimal("1000.00"));
        when(context.countPriorAccountDeviceUsage(eq(401L), eq(99L), any(Instant.class)))
                .thenReturn(0L);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isPresent();
        assertThat(hit.get().getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
        assertThat(hit.get().getReason()).contains("elevated amount");
    }
}
