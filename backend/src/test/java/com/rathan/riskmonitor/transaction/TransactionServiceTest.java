package com.rathan.riskmonitor.transaction;

import com.rathan.riskmonitor.alert.Alert;
import com.rathan.riskmonitor.alert.AlertSeverity;
import com.rathan.riskmonitor.alert.AlertStatus;
import com.rathan.riskmonitor.common.ResourceNotFoundException;
import com.rathan.riskmonitor.rules.Rule;
import com.rathan.riskmonitor.rules.RuleEngine;
import com.rathan.riskmonitor.rules.RuleHit;
import com.rathan.riskmonitor.transaction.dto.BulkTransactionResponse;
import com.rathan.riskmonitor.transaction.dto.TransactionRequest;
import com.rathan.riskmonitor.transaction.dto.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private MerchantRepository merchantRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private RuleEngine ruleEngine;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private TransactionService transactionService;

    private Account account;
    private Merchant merchant;
    private Device device;
    private Customer customer;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository,
                accountRepository,
                merchantRepository,
                deviceRepository,
                customerRepository,
                ruleEngine,
                jdbcTemplate
        );

        customer = new Customer();
        customer.setId(1L);
        customer.setHomeCountry("US");

        account = new Account();
        account.setId(10L);
        account.setCustomer(customer);

        merchant = new Merchant();
        merchant.setId(20L);

        device = new Device();
        device.setId(30L);
    }

    @Test
    void testIngestTransactionSuccess() {
        TransactionRequest request = new TransactionRequest();
        request.setAccountId(10L);
        request.setMerchantId(20L);
        request.setDeviceId(30L);
        request.setAmount(BigDecimal.valueOf(150.00));
        request.setCurrency("USD");
        request.setCountry("US");
        request.setTxnTime(Instant.now());
        request.setChannel("WEB");

        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(merchantRepository.findById(20L)).thenReturn(Optional.of(merchant));
        when(deviceRepository.findById(30L)).thenReturn(Optional.of(device));

        Transaction savedTxn = new Transaction();
        savedTxn.setId(99L);
        savedTxn.setAccount(account);
        savedTxn.setMerchant(merchant);
        savedTxn.setDevice(device);
        savedTxn.setAmount(request.getAmount());
        savedTxn.setCurrency("USD");
        savedTxn.setCountry("US");
        savedTxn.setTxnTime(request.getTxnTime());
        savedTxn.setChannel("WEB");
        savedTxn.setCreatedAt(Instant.now());

        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTxn);

        Alert alert = new Alert();
        alert.setRuleName("VelocityRule");
        when(ruleEngine.processTransaction(savedTxn)).thenReturn(List.of(alert));

        TransactionResponse response = transactionService.ingestTransaction(request);

        assertNotNull(response);
        assertEquals(99L, response.getId());
        assertEquals(1, response.getAlertsGenerated());
        assertEquals(List.of("VelocityRule"), response.getTriggeredRules());
    }

    @Test
    void testIngestTransactionAccountNotFound() {
        TransactionRequest request = new TransactionRequest();
        request.setAccountId(999L);

        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> transactionService.ingestTransaction(request));
    }

    @Test
    void testIngestTransactionMerchantNotFound() {
        TransactionRequest request = new TransactionRequest();
        request.setAccountId(10L);
        request.setMerchantId(999L);

        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(merchantRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> transactionService.ingestTransaction(request));
    }

    @Test
    void testIngestTransactionDeviceNotFound() {
        TransactionRequest request = new TransactionRequest();
        request.setAccountId(10L);
        request.setMerchantId(20L);
        request.setDeviceId(999L);

        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(merchantRepository.findById(20L)).thenReturn(Optional.of(merchant));
        when(deviceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> transactionService.ingestTransaction(request));
    }

    @Test
    void testGetTransactionFound() {
        Transaction txn = new Transaction();
        txn.setId(123L);
        txn.setAccount(account);
        txn.setMerchant(merchant);
        txn.setDevice(device);
        txn.setAmount(BigDecimal.TEN);
        txn.setCurrency("USD");
        txn.setCountry("US");
        txn.setTxnTime(Instant.now());
        txn.setChannel("MOBILE");

        when(transactionRepository.findById(123L)).thenReturn(Optional.of(txn));

        TransactionResponse resp = transactionService.getTransaction(123L);
        assertNotNull(resp);
        assertEquals(123L, resp.getId());
    }

    @Test
    void testGetTransactionNotFound() {
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> transactionService.getTransaction(999L));
    }

    @Test
    void testIngestBulk() {
        TransactionRequest req = new TransactionRequest();
        req.setAccountId(10L);
        req.setMerchantId(20L);
        req.setDeviceId(30L);
        req.setAmount(BigDecimal.valueOf(100));
        req.setCurrency("USD");
        req.setCountry("US");
        req.setTxnTime(Instant.now());
        req.setChannel("POS");

        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(merchantRepository.findById(20L)).thenReturn(Optional.of(merchant));
        when(deviceRepository.findById(30L)).thenReturn(Optional.of(device));

        Transaction savedTxn = new Transaction();
        savedTxn.setId(1L);
        savedTxn.setAccount(account);
        savedTxn.setMerchant(merchant);
        savedTxn.setDevice(device);
        savedTxn.setAmount(BigDecimal.valueOf(100));
        savedTxn.setCurrency("USD");
        savedTxn.setCountry("US");
        savedTxn.setTxnTime(req.getTxnTime());
        savedTxn.setChannel("POS");
        savedTxn.setCreatedAt(Instant.now());

        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTxn);
        when(ruleEngine.processTransaction(any())).thenReturn(List.of());

        BulkTransactionResponse bulkResp = transactionService.ingestBulk(List.of(req));
        assertEquals(1, bulkResp.getIngestedCount());
        assertEquals(0, bulkResp.getAlertsGenerated());
    }

    @Test
    void testReplayAllTransactions() {
        when(customerRepository.findAll()).thenReturn(List.of(customer));
        Rule rule = mock(Rule.class);
        when(ruleEngine.getRules()).thenReturn(List.of(rule));

        Map<String, Object> result = transactionService.replayAllTransactions();
        assertNotNull(result);
        assertTrue(result.containsKey("totalTransactions"));
        assertTrue(result.containsKey("alertsGenerated"));
        assertTrue(result.containsKey("executionTimeMs"));
    }
}
