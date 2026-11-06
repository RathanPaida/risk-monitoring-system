package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.alert.AlertSeverity;
import com.rathan.riskmonitor.transaction.Account;
import com.rathan.riskmonitor.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class VelocityRuleTest {

    private RuleProperties properties;
    private VelocityRule rule;
    private RuleContext context;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        properties = new RuleProperties();
        properties.getVelocity().setWindowMinutes(10);
        properties.getVelocity().setThresholdMedium(5);
        properties.getVelocity().setThresholdHigh(10);

        rule = new VelocityRule(properties);
        context = Mockito.mock(RuleContext.class);

        Account account = new Account();
        account.setId(101L);

        transaction = new Transaction();
        transaction.setId(1L);
        transaction.setAccount(account);
        transaction.setTxnTime(Instant.parse("2026-10-15T12:00:00Z"));
    }

    @Test
    @DisplayName("Clear Miss: Count is below medium threshold (3 <= 5)")
    void evaluate_countBelowMedium_returnsEmpty() {
        when(context.countTransactionsInWindow(eq(101L), any(Instant.class), any(Instant.class)))
                .thenReturn(3L);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isEmpty();
    }

    @Test
    @DisplayName("Boundary Case: Count exactly equals threshold (5) -> Returns Empty")
    void evaluate_countExactlyAtMediumThreshold_returnsEmpty() {
        when(context.countTransactionsInWindow(eq(101L), any(Instant.class), any(Instant.class)))
                .thenReturn(5L);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isEmpty();
    }

    @Test
    @DisplayName("Boundary Hit: Count is 1 over medium threshold (6 > 5) -> MEDIUM")
    void evaluate_countJustOverMediumThreshold_returnsMediumHit() {
        when(context.countTransactionsInWindow(eq(101L), any(Instant.class), any(Instant.class)))
                .thenReturn(6L);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isPresent();
        assertThat(hit.get().getRuleName()).isEqualTo(VelocityRule.NAME);
        assertThat(hit.get().getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
        assertThat(hit.get().getReason()).contains("Elevated transaction velocity: 6");
    }

    @Test
    @DisplayName("Boundary Case: Count exactly equals high threshold (10) -> Returns MEDIUM")
    void evaluate_countExactlyAtHighThreshold_returnsMediumHit() {
        when(context.countTransactionsInWindow(eq(101L), any(Instant.class), any(Instant.class)))
                .thenReturn(10L);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isPresent();
        assertThat(hit.get().getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
    }

    @Test
    @DisplayName("Hit Case: Count exceeds high threshold (11 > 10) -> HIGH")
    void evaluate_countOverHighThreshold_returnsHighHit() {
        when(context.countTransactionsInWindow(eq(101L), any(Instant.class), any(Instant.class)))
                .thenReturn(11L);

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isPresent();
        assertThat(hit.get().getSeverity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(hit.get().getReason()).contains("High transaction velocity: 11");
    }
}
