package com.rathan.riskmonitor.alert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query("SELECT a FROM Alert a WHERE " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:severity IS NULL OR a.severity = :severity)")
    Page<Alert> findAllFiltered(
            @Param("status") AlertStatus status,
            @Param("severity") AlertSeverity severity,
            Pageable pageable
    );

    @Query("SELECT a FROM Alert a WHERE " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(a.assignedTo.id = :userId OR (a.status = com.rathan.riskmonitor.alert.AlertStatus.OPEN AND a.assignedTo IS NULL))")
    Page<Alert> findForAnalyst(
            @Param("status") AlertStatus status,
            @Param("severity") AlertSeverity severity,
            @Param("userId") Long userId,
            Pageable pageable
    );

    Optional<Alert> findByTransaction_IdAndRuleName(Long txnId, String ruleName);
}
