package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.transaction.Customer;
import com.rathan.riskmonitor.transaction.Transaction;
import com.rathan.riskmonitor.transaction.TransactionRepository;

import java.time.Instant;
import java.util.Optional;

public class DefaultRuleContext implements RuleContext {

    private final TransactionRepository transactionRepository;
    private final Customer customer;

    public DefaultRuleContext(TransactionRepository transactionRepository, Customer customer) {
        this.transactionRepository = transactionRepository;
        this.customer = customer;
    }

    @Override
    public Customer getCustomer() {
        return customer;
    }

    @Override
    public long countTransactionsInWindow(Long accountId, Instant start, Instant end) {
        return transactionRepository.countByAccountIdAndTxnTimeBetween(accountId, start, end);
    }

    @Override
    public Double getTrailingAverageAmount(Long accountId, Instant start, Instant end) {
        return transactionRepository.findAverageAmountByAccountIdAndTxnTimeBetween(accountId, start, end);
    }

    @Override
    public Optional<Transaction> getPreviousTransaction(Long accountId, Instant beforeTime) {
        return transactionRepository.findFirstByAccount_IdAndTxnTimeBeforeOrderByTxnTimeDesc(accountId, beforeTime);
    }

    @Override
    public long countPriorAccountDeviceUsage(Long accountId, Long deviceId, Instant beforeTime) {
        return transactionRepository.countByAccountIdAndDeviceIdAndTxnTimeBefore(accountId, deviceId, beforeTime);
    }
}
