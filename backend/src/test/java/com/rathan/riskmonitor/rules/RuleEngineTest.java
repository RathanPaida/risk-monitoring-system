package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.alert.Alert;
import com.rathan.riskmonitor.alert.AlertSeverity;
import com.rathan.riskmonitor.alert.AlertService;
import com.rathan.riskmonitor.transaction.Account;
import com.rathan.riskmonitor.transaction.Customer;
import com.rathan.riskmonitor.transaction.Transaction;
import com.rathan.riskmonitor.transaction.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class RuleEngineTest {

    private Rule rule1;
    private Rule rule2;
    private AlertService alertService;
    private TransactionRepository transactionRepository;
    private RuleEngine engine;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        rule1 = Mockito.mock(Rule.class);
        rule2 = Mockito.mock(Rule.class);
        alertService = Mockito.mock(AlertService.class);
        transactionRepository = Mockito.mock(TransactionRepository.class);

        engine = new RuleEngine(List.of(rule1, rule2), alertService, transactionRepository);

        Customer customer = new Customer();
        customer.setId(1L);

        Account account = new Account();
        account.setId(10L);
        account.setCustomer(customer);

        transaction = new Transaction();
        transaction.setId(100L);
        transaction.setAccount(account);
        transaction.setTxnTime(Instant.now());
    }

    @Test
    @DisplayName("Process transaction: Multiple rules trigger alerts")
    void processTransaction_multipleRuleHits_createsAlertForEachHit() {
        RuleHit hit1 = new RuleHit("VelocityRule", AlertSeverity.HIGH, "High velocity");
        RuleHit hit2 = new RuleHit("NewDeviceRule", AlertSeverity.LOW, "New device");

        when(rule1.evaluate(eq(transaction), any(RuleContext.class))).thenReturn(Optional.of(hit1));
        when(rule2.evaluate(eq(transaction), any(RuleContext.class))).thenReturn(Optional.of(hit2));

        Alert alert1 = new Alert();
        alert1.setId(1L);
        alert1.setRuleName("VelocityRule");

        Alert alert2 = new Alert();
        alert2.setId(2L);
        alert2.setRuleName("NewDeviceRule");

        when(alertService.createAlert(eq(transaction), eq("VelocityRule"), eq(AlertSeverity.HIGH), eq("High velocity")))
                .thenReturn(alert1);
        when(alertService.createAlert(eq(transaction), eq("NewDeviceRule"), eq(AlertSeverity.LOW), eq("New device")))
                .thenReturn(alert2);

        List<Alert> results = engine.processTransaction(transaction);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getRuleName()).isEqualTo("VelocityRule");
        assertThat(results.get(1).getRuleName()).isEqualTo("NewDeviceRule");
    }

    @Test
    @DisplayName("Process transaction: No rule triggers -> Returns empty list")
    void processTransaction_noRuleHits_returnsEmptyList() {
        when(rule1.evaluate(eq(transaction), any(RuleContext.class))).thenReturn(Optional.empty());
        when(rule2.evaluate(eq(transaction), any(RuleContext.class))).thenReturn(Optional.empty());

        List<Alert> results = engine.processTransaction(transaction);

        assertThat(results).isEmpty();
        Mockito.verifyNoInteractions(alertService);
    }
}
