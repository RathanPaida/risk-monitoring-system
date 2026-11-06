package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.transaction.Customer;
import com.rathan.riskmonitor.transaction.Transaction;

import java.time.Instant;
import java.util.Optional;

public interface RuleContext {
    Customer getCustomer();
    long countTransactionsInWindow(Long accountId, Instant start, Instant end);
    Double getTrailingAverageAmount(Long accountId, Instant start, Instant end);
    Optional<Transaction> getPreviousTransaction(Long accountId, Instant beforeTime);
    long countPriorAccountDeviceUsage(Long accountId, Long deviceId, Instant beforeTime);
}
