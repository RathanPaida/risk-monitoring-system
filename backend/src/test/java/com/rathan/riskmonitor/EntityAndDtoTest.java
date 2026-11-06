package com.rathan.riskmonitor;

import com.rathan.riskmonitor.alert.*;
import com.rathan.riskmonitor.audit.AuditAction;
import com.rathan.riskmonitor.audit.AuditDto;
import com.rathan.riskmonitor.audit.AuditLog;
import com.rathan.riskmonitor.common.ErrorResponse;
import com.rathan.riskmonitor.rules.RuleHit;
import com.rathan.riskmonitor.transaction.Account;
import com.rathan.riskmonitor.transaction.Customer;
import com.rathan.riskmonitor.transaction.Device;
import com.rathan.riskmonitor.transaction.Merchant;
import com.rathan.riskmonitor.transaction.Transaction;
import com.rathan.riskmonitor.transaction.dto.BulkTransactionResponse;
import com.rathan.riskmonitor.transaction.dto.TransactionRequest;
import com.rathan.riskmonitor.transaction.dto.TransactionResponse;
import com.rathan.riskmonitor.user.Role;
import com.rathan.riskmonitor.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntityAndDtoTest {

    @Test
    void testAlertAndAlertDto() {
        Transaction txn = new Transaction();
        txn.setId(10L);

        User user = new User("analyst1", "hash", Role.ROLE_ANALYST);
        user.setId(2L);

        Alert alert = new Alert(txn, "Rule1", AlertSeverity.HIGH, AlertStatus.OPEN, "Reason1");
        alert.setId(100L);
        alert.setAssignedTo(user);
        alert.setCreatedAt(Instant.now());
        alert.setUpdatedAt(Instant.now());

        assertEquals(100L, alert.getId());
        assertEquals("Rule1", alert.getRuleName());
        assertEquals(AlertSeverity.HIGH, alert.getSeverity());
        assertEquals(AlertStatus.OPEN, alert.getStatus());
        assertEquals("Reason1", alert.getReason());
        assertEquals(user, alert.getAssignedTo());
        assertNotNull(alert.getCreatedAt());
        assertNotNull(alert.getUpdatedAt());

        AlertDto dto = new AlertDto(alert);
        assertEquals(100L, dto.getId());
        assertEquals(10L, dto.getTxnId());
        assertEquals("analyst1", dto.getAssignedToUsername());

        CloseRequest closeReq = new CloseRequest("CONFIRMED", "Test comment");
        assertEquals("CONFIRMED", closeReq.getOutcome());
        assertEquals("Test comment", closeReq.getComment());
        closeReq.setOutcome("FALSE_POSITIVE");
        closeReq.setComment("New comment");
        assertEquals("FALSE_POSITIVE", closeReq.getOutcome());
        assertEquals("New comment", closeReq.getComment());

        EscalateRequest escReq = new EscalateRequest("Escalation reason");
        assertEquals("Escalation reason", escReq.getReason());
        escReq.setReason("Updated reason");
        assertEquals("Updated reason", escReq.getReason());
    }

    @Test
    void testAuditLogAndAuditDto() {
        Alert alert = new Alert();
        alert.setId(50L);

        AuditLog log = new AuditLog(alert, AuditAction.CLAIMED, "analyst1", "OPEN", "IN_REVIEW", "Claimed alert");
        log.setId(1L);
        log.setCreatedAt(Instant.now());

        assertEquals(1L, log.getId());
        assertEquals(alert, log.getAlert());
        assertEquals(AuditAction.CLAIMED, log.getAction());
        assertEquals("analyst1", log.getActor());
        assertEquals("OPEN", log.getOldStatus());
        assertEquals("IN_REVIEW", log.getNewStatus());
        assertEquals("Claimed alert", log.getDetails());
        assertNotNull(log.getCreatedAt());

        AuditDto dto = new AuditDto(log);
        assertEquals(1L, dto.getId());
        assertEquals(50L, dto.getAlertId());
        assertEquals("CLAIMED", dto.getAction());
    }

    @Test
    void testTransactionEntitiesAndDtos() {
        Instant now = Instant.now();
        Customer c = new Customer(1L, "Alice", "US", now);
        assertEquals(1L, c.getId());
        assertEquals("Alice", c.getName());
        assertEquals("US", c.getHomeCountry());
        assertEquals(now, c.getCreatedAt());

        Account a = new Account(10L, c, "CHECKING", now, "ACTIVE");
        assertEquals(10L, a.getId());
        assertEquals(c, a.getCustomer());
        assertEquals("CHECKING", a.getAccountType());
        assertEquals(now, a.getOpenedAt());
        assertEquals("ACTIVE", a.getStatus());

        Merchant m = new Merchant(20L, "MerchantCorp", "RETAIL", "US");
        assertEquals(20L, m.getId());
        assertEquals("MerchantCorp", m.getName());
        assertEquals("RETAIL", m.getCategory());
        assertEquals("US", m.getCountry());

        Device d = new Device(30L, c, "fp-xyz", now);
        assertEquals(30L, d.getId());
        assertEquals(c, d.getCustomer());
        assertEquals("fp-xyz", d.getDeviceHash());
        assertEquals(now, d.getFirstSeenAt());

        Transaction t = new Transaction();
        t.setId(100L);
        t.setAccount(a);
        t.setMerchant(m);
        t.setDevice(d);
        t.setAmount(BigDecimal.valueOf(99.99));
        t.setCurrency("USD");
        t.setCountry("US");
        t.setTxnTime(now);
        t.setChannel("ONLINE");
        t.setCreatedAt(now);

        assertEquals(100L, t.getId());
        assertEquals(a, t.getAccount());
        assertEquals(m, t.getMerchant());
        assertEquals(d, t.getDevice());
        assertEquals(BigDecimal.valueOf(99.99), t.getAmount());
        assertEquals("USD", t.getCurrency());
        assertEquals("US", t.getCountry());
        assertEquals(now, t.getTxnTime());
        assertEquals("ONLINE", t.getChannel());
        assertEquals(now, t.getCreatedAt());

        TransactionRequest req = new TransactionRequest();
        req.setId(1L);
        req.setAccountId(10L);
        req.setMerchantId(20L);
        req.setDeviceId(30L);
        req.setAmount(BigDecimal.valueOf(50));
        req.setCurrency("EUR");
        req.setCountry("FR");
        req.setTxnTime(now);
        req.setChannel("POS");
        assertEquals(1L, req.getId());
        assertEquals(10L, req.getAccountId());
        assertEquals(20L, req.getMerchantId());
        assertEquals(30L, req.getDeviceId());
        assertEquals(BigDecimal.valueOf(50), req.getAmount());
        assertEquals("EUR", req.getCurrency());
        assertEquals("FR", req.getCountry());
        assertEquals(now, req.getTxnTime());
        assertEquals("POS", req.getChannel());

        TransactionResponse resp = new TransactionResponse();
        resp.setId(1L);
        resp.setAccountId(10L);
        resp.setMerchantId(20L);
        resp.setDeviceId(30L);
        resp.setAmount(BigDecimal.valueOf(50));
        resp.setCurrency("EUR");
        resp.setCountry("FR");
        resp.setTxnTime(now);
        resp.setChannel("POS");
        resp.setCreatedAt(now);
        resp.setAlertsGenerated(1);
        resp.setTriggeredRules(List.of("RuleX"));
        assertEquals(1L, resp.getId());
        assertEquals(1, resp.getAlertsGenerated());
        assertEquals(List.of("RuleX"), resp.getTriggeredRules());

        BulkTransactionResponse bulk = new BulkTransactionResponse();
        bulk.setIngestedCount(5);
        bulk.setAlertsGenerated(2);
        assertEquals(5, bulk.getIngestedCount());
        assertEquals(2, bulk.getAlertsGenerated());

        RuleHit hit = new RuleHit("Rule1", AlertSeverity.HIGH, "Reason");
        assertEquals("Rule1", hit.getRuleName());
        assertEquals(AlertSeverity.HIGH, hit.getSeverity());
        assertEquals("Reason", hit.getReason());

        ErrorResponse err = new ErrorResponse();
        err.setStatus(400);
        err.setError("Bad Request");
        err.setMessage("Invalid input");
        err.setPath("/api/test");
        err.setTimestamp(now);
        err.setDetails(List.of("detail 1"));
        assertEquals(400, err.getStatus());
        assertEquals("Bad Request", err.getError());
        assertEquals("Invalid input", err.getMessage());
        assertEquals("/api/test", err.getPath());
        assertEquals(now, err.getTimestamp());
        assertEquals(List.of("detail 1"), err.getDetails());
    }
}
