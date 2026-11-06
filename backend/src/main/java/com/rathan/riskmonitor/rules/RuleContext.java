package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.transaction.Customer;
import com.rathan.riskmonitor.transaction.Transaction;
import com.rathan.riskmonitor.transaction.TransactionRepository;

import java.time.Instant;
import java.util.Optional;

public class RuleContext {

    private final TransactionRepository transactionRepository;
    private final Customer customer;
    private Optional<Double> cachedTrailingAverage = Optional.empty();
    private Optional<Optional<Transaction>> cachedPreviousTransaction = Optional.empty();

    public RuleContext(TransactionRepository transactionRepository, Customer customer) {
        this.transactionRepository = transactionRepository;
        this.customer = customer;
    }

    public Customer getCustomer() {
        return customer;
    }

    public long countTransactionsInWindow(Long accountId, Instant start, Instant end) {
        return transactionRepository.countByAccountIdAndTxnTimeBetween(accountId, start, end);
    }

    public Double getTrailingAverageAmount(Long accountId, Instant start, Instant end) {
        return transactionRepository.findAverageAmountByAccountIdAndTxnTimeBetween(accountId, start, end);
    }

    public Optional<Transaction> getPreviousTransaction(Long accountId, Instant beforeTime) {
        return transactionRepository.findFirstByAccount_IdAndTxnTimeBeforeOrderByTxnTimeDesc(accountId, beforeTime);
    }

    public long countPriorAccountDeviceUsage(Long accountId, Long deviceId, Instant beforeTime) {
        return transactionRepository.countByAccountIdAndDeviceIdAndTxnTimeBefore(accountId, deviceId, beforeTime);
    }
}
