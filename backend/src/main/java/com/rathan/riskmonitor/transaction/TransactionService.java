package com.rathan.riskmonitor.transaction;

import com.rathan.riskmonitor.alert.Alert;
import com.rathan.riskmonitor.alert.AlertSeverity;
import com.rathan.riskmonitor.alert.AlertStatus;
import com.rathan.riskmonitor.common.ResourceNotFoundException;
import com.rathan.riskmonitor.rules.FastReplayContext;
import com.rathan.riskmonitor.rules.Rule;
import com.rathan.riskmonitor.rules.RuleEngine;
import com.rathan.riskmonitor.rules.RuleHit;
import com.rathan.riskmonitor.transaction.dto.BulkTransactionResponse;
import com.rathan.riskmonitor.transaction.dto.TransactionRequest;
import com.rathan.riskmonitor.transaction.dto.TransactionResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final MerchantRepository merchantRepository;
    private final DeviceRepository deviceRepository;
    private final CustomerRepository customerRepository;
    private final RuleEngine ruleEngine;
    private final JdbcTemplate jdbcTemplate;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            MerchantRepository merchantRepository,
            DeviceRepository deviceRepository,
            CustomerRepository customerRepository,
            RuleEngine ruleEngine,
            JdbcTemplate jdbcTemplate
    ) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.merchantRepository = merchantRepository;
        this.deviceRepository = deviceRepository;
        this.customerRepository = customerRepository;
        this.ruleEngine = ruleEngine;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public TransactionResponse ingestTransaction(TransactionRequest request) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + request.getAccountId()));
        Merchant merchant = merchantRepository.findById(request.getMerchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found with id: " + request.getMerchantId()));
        Device device = deviceRepository.findById(request.getDeviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Device not found with id: " + request.getDeviceId()));

        Transaction txn = new Transaction();
        if (request.getId() != null) {
            txn.setId(request.getId());
        }
        txn.setAccount(account);
        txn.setMerchant(merchant);
        txn.setDevice(device);
        txn.setAmount(request.getAmount());
        txn.setCurrency(request.getCurrency());
        txn.setCountry(request.getCountry());
        txn.setTxnTime(request.getTxnTime());
        txn.setChannel(request.getChannel());
        txn.setCreatedAt(Instant.now());

        Transaction saved = transactionRepository.save(txn);
        List<Alert> alerts = ruleEngine.processTransaction(saved);

        return mapToResponse(saved, alerts);
    }

    @Transactional
    public BulkTransactionResponse ingestBulk(List<TransactionRequest> requests) {
        int totalAlerts = 0;
        int count = 0;
        for (TransactionRequest req : requests) {
            TransactionResponse resp = ingestTransaction(req);
            totalAlerts += resp.getAlertsGenerated();
            count++;
        }
        return new BulkTransactionResponse(count, totalAlerts);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Long id) {
        Transaction txn = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        return mapToResponse(txn, List.of());
    }

    @Transactional
    public Map<String, Object> replayAllTransactions() {
        long startTime = System.currentTimeMillis();

        List<Customer> customers = customerRepository.findAll();
        FastReplayContext replayContext = new FastReplayContext(customers);
        List<Rule> rules = ruleEngine.getRules();

        String selectSql = "SELECT t.id, t.account_id, t.merchant_id, t.device_id, t.amount, t.currency, t.country, t.txn_time, t.channel, " +
                "a.customer_id, c.home_country " +
                "FROM transactions t " +
                "JOIN accounts a ON t.account_id = a.id " +
                "JOIN customers c ON a.customer_id = c.id " +
                "ORDER BY t.txn_time ASC";

        List<Object[]> pendingAlerts = new ArrayList<>();
        int[] counter = new int[2]; // [totalProcessed, alertsGenerated]

        jdbcTemplate.query(selectSql, rs -> {
            Transaction txn = new Transaction();
            txn.setId(rs.getLong("id"));

            Account acc = new Account();
            acc.setId(rs.getLong("account_id"));
            Customer cust = new Customer();
            cust.setId(rs.getLong("customer_id"));
            cust.setHomeCountry(rs.getString("home_country"));
            acc.setCustomer(cust);
            txn.setAccount(acc);

            Device dev = new Device();
            dev.setId(rs.getLong("device_id"));
            txn.setDevice(dev);

            txn.setAmount(rs.getBigDecimal("amount"));
            txn.setCurrency(rs.getString("currency"));
            txn.setCountry(rs.getString("country"));
            Timestamp ts = rs.getTimestamp("txn_time");
            txn.setTxnTime(ts.toInstant());
            txn.setChannel(rs.getString("channel"));

            replayContext.prepareForTransaction(txn);

            for (Rule rule : rules) {
                Optional<RuleHit> hitOpt = rule.evaluate(txn, replayContext);
                if (hitOpt.isPresent()) {
                    RuleHit hit = hitOpt.get();
                    pendingAlerts.add(new Object[]{
                            txn.getId(),
                            hit.getRuleName(),
                            hit.getSeverity().name(),
                            AlertStatus.OPEN.name(),
                            hit.getReason(),
                            Timestamp.from(Instant.now()),
                            Timestamp.from(Instant.now())
                    });
                    counter[1]++;
                }
            }

            replayContext.updateAfterTransaction(txn);
            counter[0]++;
        });

        // Batch insert generated alerts into alerts table
        String insertAlertSql = "INSERT INTO alerts (txn_id, rule_name, severity, status, reason, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";

        String insertAuditSql = "INSERT INTO audit_log (alert_id, action, actor, old_status, new_status, details, created_at) " +
                "VALUES (?, 'CREATED', 'system', NULL, 'OPEN', ?, ?)";

        List<Object[]> auditEntries = new ArrayList<>();

        for (Object[] alertParams : pendingAlerts) {
            Long alertId = jdbcTemplate.queryForObject(
                    insertAlertSql,
                    Long.class,
                    alertParams[0], alertParams[1], alertParams[2], alertParams[3], alertParams[4], alertParams[5], alertParams[6]
            );
            auditEntries.add(new Object[]{
                    alertId,
                    "Replay alert created by " + alertParams[1] + ": " + alertParams[4],
                    alertParams[5]
            });
        }

        if (!auditEntries.isEmpty()) {
            jdbcTemplate.batchUpdate(insertAuditSql, auditEntries);
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        Map<String, Object> result = new HashMap<>();
        result.put("totalTransactions", counter[0]);
        result.put("alertsGenerated", counter[1]);
        result.put("executionTimeMs", elapsedMs);
        return result;
    }

    private TransactionResponse mapToResponse(Transaction txn, List<Alert> alerts) {
        TransactionResponse resp = new TransactionResponse();
        resp.setId(txn.getId());
        resp.setAccountId(txn.getAccount().getId());
        resp.setMerchantId(txn.getMerchant().getId());
        resp.setDeviceId(txn.getDevice().getId());
        resp.setAmount(txn.getAmount());
        resp.setCurrency(txn.getCurrency());
        resp.setCountry(txn.getCountry());
        resp.setTxnTime(txn.getTxnTime());
        resp.setChannel(txn.getChannel());
        resp.setCreatedAt(txn.getCreatedAt());
        resp.setAlertsGenerated(alerts.size());
        resp.setTriggeredRules(alerts.stream().map(Alert::getRuleName).collect(Collectors.toList()));
        return resp;
    }
}
