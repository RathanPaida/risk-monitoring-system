package com.rathan.riskmonitor.transaction;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.account.id = :accountId AND t.txnTime >= :start AND t.txnTime <= :end")
    long countByAccountIdAndTxnTimeBetween(
            @Param("accountId") Long accountId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("SELECT AVG(t.amount) FROM Transaction t WHERE t.account.id = :accountId AND t.txnTime >= :start AND t.txnTime <= :end")
    Double findAverageAmountByAccountIdAndTxnTimeBetween(
            @Param("accountId") Long accountId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    Optional<Transaction> findFirstByAccount_IdAndTxnTimeBeforeOrderByTxnTimeDesc(Long accountId, Instant txnTime);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.account.id = :accountId AND t.device.id = :deviceId AND t.txnTime < :beforeTime")
    long countByAccountIdAndDeviceIdAndTxnTimeBefore(
            @Param("accountId") Long accountId,
            @Param("deviceId") Long deviceId,
            @Param("beforeTime") Instant beforeTime
    );

    List<Transaction> findAllByOrderByTxnTimeAsc(Pageable pageable);
}
