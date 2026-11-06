package com.rathan.riskmonitor.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE a.alert.id = :alertId ORDER BY a.createdAt ASC")
    List<AuditLog> findByAlertIdOrderByCreatedAtAsc(@Param("alertId") Long alertId);
}
