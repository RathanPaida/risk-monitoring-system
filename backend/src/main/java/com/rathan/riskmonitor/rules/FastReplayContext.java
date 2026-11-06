package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.transaction.Customer;
import com.rathan.riskmonitor.transaction.Transaction;

import java.time.Instant;
import java.util.*;

public class FastReplayContext implements RuleContext {

    private final Map<Long, Customer> customerMap;
    private final Map<Long, ArrayDeque<Instant>> accountVelocityMap = new HashMap<>();
    private final Map<Long, ArrayDeque<AmountTimestamp>> accountAmountMap = new HashMap<>();
    private final Map<Long, Transaction> accountLastTxnMap = new HashMap<>();
    private final Set<String> accountDeviceSeenSet = new HashSet<>();

    private Customer currentCustomer;

    private static class AmountTimestamp {
        final double amount;
        final Instant timestamp;

        AmountTimestamp(double amount, Instant timestamp) {
            this.amount = amount;
            this.timestamp = timestamp;
        }
    }

    public FastReplayContext(List<Customer> customers) {
        this.customerMap = new HashMap<>();
        for (Customer c : customers) {
            this.customerMap.put(c.getId(), c);
        }
    }

    public void prepareForTransaction(Transaction txn) {
        if (txn.getAccount() != null && txn.getAccount().getCustomer() != null) {
            this.currentCustomer = customerMap.get(txn.getAccount().getCustomer().getId());
        } else {
            this.currentCustomer = null;
        }
    }

    public void updateAfterTransaction(Transaction txn) {
        Long accountId = txn.getAccount().getId();
        Instant time = txn.getTxnTime();

        accountVelocityMap.computeIfAbsent(accountId, k -> new ArrayDeque<>()).addLast(time);

        if (txn.getAmount() != null) {
            accountAmountMap.computeIfAbsent(accountId, k -> new ArrayDeque<>())
                    .addLast(new AmountTimestamp(txn.getAmount().doubleValue(), time));
        }

        accountLastTxnMap.put(accountId, txn);

        if (txn.getDevice() != null) {
            accountDeviceSeenSet.add(accountId + "_" + txn.getDevice().getId());
        }
    }

    @Override
    public Customer getCustomer() {
        return currentCustomer;
    }

    @Override
    public long countTransactionsInWindow(Long accountId, Instant start, Instant end) {
        ArrayDeque<Instant> deque = accountVelocityMap.get(accountId);
        if (deque == null || deque.isEmpty()) {
            return 0;
        }
        while (!deque.isEmpty() && deque.peekFirst().isBefore(start)) {
            deque.pollFirst();
        }
        return deque.size();
    }

    @Override
    public Double getTrailingAverageAmount(Long accountId, Instant start, Instant end) {
        ArrayDeque<AmountTimestamp> deque = accountAmountMap.get(accountId);
        if (deque == null || deque.isEmpty()) {
            return null;
        }
        while (!deque.isEmpty() && deque.peekFirst().timestamp.isBefore(start)) {
            deque.pollFirst();
        }
        if (deque.isEmpty()) {
            return null;
        }
        double sum = 0.0;
        for (AmountTimestamp item : deque) {
            sum += item.amount;
        }
        return sum / deque.size();
    }

    @Override
    public Optional<Transaction> getPreviousTransaction(Long accountId, Instant beforeTime) {
        Transaction prev = accountLastTxnMap.get(accountId);
        if (prev != null && prev.getTxnTime().isBefore(beforeTime)) {
            return Optional.of(prev);
        }
        return Optional.empty();
    }

    @Override
    public long countPriorAccountDeviceUsage(Long accountId, Long deviceId, Instant beforeTime) {
        return accountDeviceSeenSet.contains(accountId + "_" + deviceId) ? 1L : 0L;
    }
}
