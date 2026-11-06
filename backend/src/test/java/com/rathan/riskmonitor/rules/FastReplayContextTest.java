package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.transaction.Account;
import com.rathan.riskmonitor.transaction.Customer;
import com.rathan.riskmonitor.transaction.Device;
import com.rathan.riskmonitor.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FastReplayContextTest {

    private FastReplayContext context;
    private Customer customer;
    private Account account;
    private Device device;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(10L);
        customer.setHomeCountry("US");

        account = new Account();
        account.setId(100L);
        account.setCustomer(customer);

        device = new Device();
        device.setId(500L);

        context = new FastReplayContext(List.of(customer));
    }

    @Test
    void testPrepareAndGetCustomer() {
        Transaction txn = new Transaction();
        txn.setAccount(account);

        context.prepareForTransaction(txn);
        assertNotNull(context.getCustomer());
        assertEquals("US", context.getCustomer().getHomeCountry());

        // Null account / customer test
        Transaction nullTxn = new Transaction();
        context.prepareForTransaction(nullTxn);
        assertNull(context.getCustomer());
    }

    @Test
    void testVelocityCount() {
        Instant now = Instant.now();
        assertEquals(0, context.countTransactionsInWindow(100L, now.minus(10, ChronoUnit.MINUTES), now));

        Transaction txn1 = createTxn(now.minus(5, ChronoUnit.MINUTES), 50.0);
        context.updateAfterTransaction(txn1);

        Transaction txn2 = createTxn(now.minus(2, ChronoUnit.MINUTES), 70.0);
        context.updateAfterTransaction(txn2);

        assertEquals(2, context.countTransactionsInWindow(100L, now.minus(10, ChronoUnit.MINUTES), now));

        // Window eviction test
        assertEquals(1, context.countTransactionsInWindow(100L, now.minus(3, ChronoUnit.MINUTES), now));
    }

    @Test
    void testTrailingAverageAmount() {
        Instant now = Instant.now();
        assertNull(context.getTrailingAverageAmount(100L, now.minus(30, ChronoUnit.DAYS), now));

        Transaction txn1 = createTxn(now.minus(10, ChronoUnit.DAYS), 100.0);
        context.updateAfterTransaction(txn1);

        Transaction txn2 = createTxn(now.minus(5, ChronoUnit.DAYS), 200.0);
        context.updateAfterTransaction(txn2);

        Double avg = context.getTrailingAverageAmount(100L, now.minus(30, ChronoUnit.DAYS), now);
        assertNotNull(avg);
        assertEquals(150.0, avg, 0.001);

        // Window eviction test
        Double recentAvg = context.getTrailingAverageAmount(100L, now.minus(7, ChronoUnit.DAYS), now);
        assertNotNull(recentAvg);
        assertEquals(200.0, recentAvg, 0.001);
    }

    @Test
    void testPreviousTransactionAndDeviceSeen() {
        Instant now = Instant.now();
        assertTrue(context.getPreviousTransaction(100L, now).isEmpty());
        assertEquals(0, context.countPriorAccountDeviceUsage(100L, 500L, now));

        Transaction txn = createTxn(now, 100.0);
        context.updateAfterTransaction(txn);

        Optional<Transaction> prev = context.getPreviousTransaction(100L, now.plusSeconds(60));
        assertTrue(prev.isPresent());
        assertEquals(100.0, prev.get().getAmount().doubleValue());
        assertEquals(1, context.countPriorAccountDeviceUsage(100L, 500L, now));
        assertEquals(0, context.countPriorAccountDeviceUsage(100L, 999L, now));
    }

    private Transaction createTxn(Instant time, double amount) {
        Transaction txn = new Transaction();
        txn.setId(1L);
        txn.setAccount(account);
        txn.setDevice(device);
        txn.setTxnTime(time);
        txn.setAmount(BigDecimal.valueOf(amount));
        txn.setCountry("US");
        return txn;
    }
}
