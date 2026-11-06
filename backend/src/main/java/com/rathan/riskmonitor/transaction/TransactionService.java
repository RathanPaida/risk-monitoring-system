package com.rathan.riskmonitor.transaction;

import com.rathan.riskmonitor.alert.Alert;
import com.rathan.riskmonitor.common.ResourceNotFoundException;
import com.rathan.riskmonitor.rules.RuleEngine;
import com.rathan.riskmonitor.transaction.dto.BulkTransactionResponse;
import com.rathan.riskmonitor.transaction.dto.TransactionRequest;
import com.rathan.riskmonitor.transaction.dto.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final MerchantRepository merchantRepository;
    private final DeviceRepository deviceRepository;
    private final RuleEngine ruleEngine;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            MerchantRepository merchantRepository,
            DeviceRepository deviceRepository,
            RuleEngine ruleEngine
    ) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.merchantRepository = merchantRepository;
        this.deviceRepository = deviceRepository;
        this.ruleEngine = ruleEngine;
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
        int pageSize = 1000;
        int pageNumber = 0;
        long totalProcessed = 0;
        int totalAlerts = 0;

        Page<Transaction> page;
        do {
            page = transactionRepository.findAll(PageRequest.of(pageNumber, pageSize, org.springframework.data.domain.Sort.by("txnTime").ascending()));
            for (Transaction txn : page.getContent()) {
                List<Alert> alerts = ruleEngine.processTransaction(txn);
                totalAlerts += alerts.size();
                totalProcessed++;
            }
            pageNumber++;
        } while (page.hasNext());

        long elapsedMs = System.currentTimeMillis() - startTime;
        Map<String, Object> result = new HashMap<>();
        result.put("totalTransactions", totalProcessed);
        result.put("alertsGenerated", totalAlerts);
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
