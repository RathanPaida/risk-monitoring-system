package com.rathan.riskmonitor.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    @Query("SELECT COUNT(d) FROM Device d WHERE d.customer.id = :customerId AND d.firstSeenAt < :time")
    long countByCustomerIdAndFirstSeenAtBefore(@Param("customerId") Long customerId, @Param("time") Instant time);
}
