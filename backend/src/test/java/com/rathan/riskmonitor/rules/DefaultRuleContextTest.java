package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.transaction.Customer;
import com.rathan.riskmonitor.transaction.Transaction;
import com.rathan.riskmonitor.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class DefaultRuleContextTest {

    @Test
    void testDelegationToRepository() {
        TransactionRepository repository = mock(TransactionRepository.class);
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setHomeCountry("US");

        DefaultRuleContext context = new DefaultRuleContext(repository, customer);
        assertEquals(customer, context.getCustomer());

        Instant start = Instant.now().minusSeconds(600);
        Instant end = Instant.now();

        when(repository.countByAccountIdAndTxnTimeBetween(10L, start, end)).thenReturn(5L);
        assertEquals(5L, context.countTransactionsInWindow(10L, start, end));

        when(repository.findAverageAmountByAccountIdAndTxnTimeBetween(10L, start, end)).thenReturn(120.5);
        assertEquals(120.5, context.getTrailingAverageAmount(10L, start, end));

        Transaction txn = new Transaction();
        when(repository.findFirstByAccount_IdAndTxnTimeBeforeOrderByTxnTimeDesc(10L, end)).thenReturn(Optional.of(txn));
        assertTrue(context.getPreviousTransaction(10L, end).isPresent());

        when(repository.countByAccountIdAndDeviceIdAndTxnTimeBefore(10L, 20L, end)).thenReturn(1L);
        assertEquals(1L, context.countPriorAccountDeviceUsage(10L, 20L, end));
    }
}
