package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.alert.AlertSeverity;
import com.rathan.riskmonitor.transaction.Account;
import com.rathan.riskmonitor.transaction.Customer;
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

class GeoMismatchRuleTest {

    private RuleProperties properties;
    private GeoMismatchRule rule;
    private RuleContext context;
    private Customer customer;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        properties = new RuleProperties();
        properties.getGeoMismatch().setHopWindowMinutes(60);

        rule = new GeoMismatchRule(properties);
        context = Mockito.mock(RuleContext.class);

        customer = new Customer();
        customer.setId(301L);
        customer.setHomeCountry("US");

        Account account = new Account();
        account.setId(301L);
        account.setCustomer(customer);

        transaction = new Transaction();
        transaction.setId(25L);
        transaction.setAccount(account);
        transaction.setCountry("US");
        transaction.setTxnTime(Instant.parse("2026-10-15T12:00:00Z"));

        when(context.getCustomer()).thenReturn(customer);
    }

    @Test
    @DisplayName("Clear Miss: Transaction is in customer home country, no sudden hop")
    void evaluate_homeCountryNoHop_returnsEmpty() {
        when(context.getPreviousTransaction(eq(301L), any(Instant.class)))
                .thenReturn(Optional.empty());

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isEmpty();
    }

    @Test
    @DisplayName("Hit Case: Transaction country differs from customer home country")
    void evaluate_foreignCountryMismatch_returnsMediumHit() {
        transaction.setCountry("GB");
        when(context.getPreviousTransaction(eq(301L), any(Instant.class)))
                .thenReturn(Optional.empty());

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isPresent();
        assertThat(hit.get().getRuleName()).isEqualTo(GeoMismatchRule.NAME);
        assertThat(hit.get().getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
        assertThat(hit.get().getReason()).contains("differs from customer home country");
    }

    @Test
    @DisplayName("Hit Case: Sudden geo-hop within window (hop in 30 min from different country)")
    void evaluate_suddenGeoHopWithinWindow_returnsMediumHit() {
        Transaction prevTxn = new Transaction();
        prevTxn.setCountry("CA");
        prevTxn.setTxnTime(Instant.parse("2026-10-15T11:30:00Z")); // 30 minutes before

        when(context.getPreviousTransaction(eq(301L), any(Instant.class)))
                .thenReturn(Optional.of(prevTxn));

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isPresent();
        assertThat(hit.get().getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
        assertThat(hit.get().getReason()).contains("Sudden geo-hop");
    }

    @Test
    @DisplayName("Boundary Case: Hop occurred 61 minutes ago (> 60 min window) -> Returns Empty")
    void evaluate_geoHopOutsideWindow_returnsEmpty() {
        Transaction prevTxn = new Transaction();
        prevTxn.setCountry("CA");
        prevTxn.setTxnTime(Instant.parse("2026-10-15T10:59:00Z")); // 61 minutes before

        when(context.getPreviousTransaction(eq(301L), any(Instant.class)))
                .thenReturn(Optional.of(prevTxn));

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isEmpty();
    }

    @Test
    @DisplayName("Boundary Hit: Hop occurred exactly at 60 minutes (<= 60 min window) -> MEDIUM")
    void evaluate_geoHopExactlyAtWindowBoundary_returnsMediumHit() {
        Transaction prevTxn = new Transaction();
        prevTxn.setCountry("CA");
        prevTxn.setTxnTime(Instant.parse("2026-10-15T11:00:00Z")); // exactly 60 minutes before

        when(context.getPreviousTransaction(eq(301L), any(Instant.class)))
                .thenReturn(Optional.of(prevTxn));

        Optional<RuleHit> hit = rule.evaluate(transaction, context);

        assertThat(hit).isPresent();
        assertThat(hit.get().getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
    }
}
