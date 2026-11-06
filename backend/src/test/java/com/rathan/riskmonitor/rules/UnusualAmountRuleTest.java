package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.alert.AlertSeverity;
import com.rathan.riskmonitor.transaction.Account;
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

class UnusualAmountRuleTest {

    private RuleProperties properties;
    private UnusualAmountRule rule;
    private RuleContext context;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        properties = new RuleProperties();
        properties.getUnusualAmount().setMinAmount(500.0);
        properties.getUnusualAmount().setMultiplierMedium(5.0);
        properties.getUnusualAmount().setMultiplierHigh(10.0);
        properties.getUnusualAmount().setTrailingDays(30);

        rule = new UnusualAmountRule(properties);
        context = Mockito.mock(RuleContext.class);

        Account account = new Account();
        account.setId(201L);

        transaction = new Transaction();
        transaction.setId(10L);
        transaction.setAccount(account);
        transaction.setTxnTime(Instant.parse("2026-10-15T12:00:00Z"));
    }

    @Test
    @DisplayName("Clear Miss: Amount is below minimum floor ($450 < $500)")
    void evaluate_amountBelowMinFloor_returnsEmpty() {
        transaction.setAmount(new BigDecimal("450.00"));
        when(context.getTrailingAverageAmount(eq(201L), any(Instant.class), any(Instant.class)))
                .thenReturn(10.0); // 45x average, but below floor

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isEmpty();
    }

    @Test
    @DisplayName("Clear Miss: Ratio is below medium multiplier (4.0x <= 5.0x)")
    void evaluate_ratioBelowMultiplier_returnsEmpty() {
        transaction.setAmount(new BigDecimal("600.00"));
        when(context.getTrailingAverageAmount(eq(201L), any(Instant.class), any(Instant.class)))
                .thenReturn(150.0); // 4.0x

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isEmpty();
    }

    @Test
    @DisplayName("Clear Miss: No trailing history (avg is null or zero)")
    void evaluate_noTrailingHistory_returnsEmpty() {
        transaction.setAmount(new BigDecimal("1000.00"));
        when(context.getTrailingAverageAmount(eq(201L), any(Instant.class), any(Instant.class)))
                .thenReturn(null);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isEmpty();
    }

    @Test
    @DisplayName("Boundary Case: Ratio exactly equals medium multiplier (500 / 100 = 5.0x) -> Returns Empty")
    void evaluate_ratioExactlyAtMediumThreshold_returnsEmpty() {
        transaction.setAmount(new BigDecimal("500.00"));
        when(context.getTrailingAverageAmount(eq(201L), any(Instant.class), any(Instant.class)))
                .thenReturn(100.0);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isEmpty();
    }

    @Test
    @DisplayName("Boundary Hit: Ratio just exceeds medium multiplier (510 / 100 = 5.1x) -> MEDIUM")
    void evaluate_ratioJustOverMediumThreshold_returnsMediumHit() {
        transaction.setAmount(new BigDecimal("510.00"));
        when(context.getTrailingAverageAmount(eq(201L), any(Instant.class), any(Instant.class)))
                .thenReturn(100.0);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isPresent();
        assertThat(hit.get().getRuleName()).isEqualTo(UnusualAmountRule.NAME);
        assertThat(hit.get().getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
        assertThat(hit.get().getReason()).contains("5.1x");
    }

    @Test
    @DisplayName("Boundary Case: Ratio exactly equals high multiplier (1000 / 100 = 10.0x) -> Returns MEDIUM")
    void evaluate_ratioExactlyAtHighThreshold_returnsMediumHit() {
        transaction.setAmount(new BigDecimal("1000.00"));
        when(context.getTrailingAverageAmount(eq(201L), any(Instant.class), any(Instant.class)))
                .thenReturn(100.0);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isPresent();
        assertThat(hit.get().getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
    }

    @Test
    @DisplayName("Hit Case: Ratio exceeds high multiplier (1500 / 100 = 15.0x) -> HIGH")
    void evaluate_ratioOverHighThreshold_returnsHighHit() {
        transaction.setAmount(new BigDecimal("1500.00"));
        when(context.getTrailingAverageAmount(eq(201L), any(Instant.class), any(Instant.class)))
                .thenReturn(100.0);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isPresent();
        assertThat(hit.get().getSeverity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(hit.get().getReason()).contains("Extremely unusual amount");
    }
}
