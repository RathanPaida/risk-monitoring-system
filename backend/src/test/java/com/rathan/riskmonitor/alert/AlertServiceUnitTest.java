package com.rathan.riskmonitor.alert;

import com.rathan.riskmonitor.audit.AuditAction;
import com.rathan.riskmonitor.audit.AuditService;
import com.rathan.riskmonitor.common.ConflictException;
import com.rathan.riskmonitor.common.ResourceNotFoundException;
import com.rathan.riskmonitor.transaction.Transaction;
import com.rathan.riskmonitor.user.Role;
import com.rathan.riskmonitor.user.User;
import com.rathan.riskmonitor.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceUnitTest {

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private UserRepository userRepository;

    private AlertService alertService;
    private User analystUser;
    private User managerUser;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(alertRepository, auditService, userRepository);

        analystUser = new User();
        analystUser.setId(1L);
        analystUser.setUsername("analyst1");
        analystUser.setRole(Role.ROLE_ANALYST);

        managerUser = new User();
        managerUser.setId(2L);
        managerUser.setUsername("manager1");
        managerUser.setRole(Role.ROLE_MANAGER);
    }

    @Test
    void testCreateAlert() {
        Transaction txn = new Transaction();
        txn.setId(10L);

        Alert alert = new Alert(txn, "VelocityRule", AlertSeverity.HIGH, AlertStatus.OPEN, "Too fast");
        alert.setId(100L);

        when(alertRepository.save(any(Alert.class))).thenReturn(alert);

        Alert created = alertService.createAlert(txn, "VelocityRule", AlertSeverity.HIGH, "Too fast");
        assertNotNull(created);
        assertEquals(100L, created.getId());
        verify(auditService).recordAction(eq(alert), eq(AuditAction.CREATED), eq("system"), isNull(), eq("OPEN"), anyString());
    }

    @Test
    void testClaimAlertSuccess() {
        Alert alert = new Alert();
        alert.setId(5L);
        alert.setStatus(AlertStatus.OPEN);

        when(alertRepository.findById(5L)).thenReturn(Optional.of(alert));
        when(userRepository.findByUsername("analyst1")).thenReturn(Optional.of(analystUser));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        Alert claimed = alertService.claimAlert(5L, "analyst1");
        assertEquals(AlertStatus.IN_REVIEW, claimed.getStatus());
        assertEquals(analystUser, claimed.getAssignedTo());
        verify(auditService).recordAction(eq(alert), eq(AuditAction.CLAIMED), eq("analyst1"), eq("OPEN"), eq("IN_REVIEW"), anyString());
    }

    @Test
    void testClaimAlertConflict() {
        Alert alert = new Alert();
        alert.setId(5L);
        alert.setStatus(AlertStatus.IN_REVIEW);

        when(alertRepository.findById(5L)).thenReturn(Optional.of(alert));
        assertThrows(ConflictException.class, () -> alertService.claimAlert(5L, "analyst1"));
    }

    @Test
    void testEscalateAlertSuccess() {
        Alert alert = new Alert();
        alert.setId(5L);
        alert.setStatus(AlertStatus.IN_REVIEW);

        when(alertRepository.findById(5L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        Alert escalated = alertService.escalateAlert(5L, "analyst1", "Needs senior review");
        assertEquals(AlertStatus.ESCALATED, escalated.getStatus());
        assertEquals("Needs senior review", escalated.getReason());
        verify(auditService).recordAction(eq(alert), eq(AuditAction.ESCALATED), eq("analyst1"), eq("IN_REVIEW"), eq("ESCALATED"), anyString());
    }

    @Test
    void testEscalateAlertConflict() {
        Alert alert = new Alert();
        alert.setId(5L);
        alert.setStatus(AlertStatus.OPEN);

        when(alertRepository.findById(5L)).thenReturn(Optional.of(alert));
        assertThrows(ConflictException.class, () -> alertService.escalateAlert(5L, "analyst1", "invalid"));
    }

    @Test
    void testCloseAlertInReviewByAnalyst() {
        Alert alert = new Alert();
        alert.setId(5L);
        alert.setStatus(AlertStatus.IN_REVIEW);

        when(alertRepository.findById(5L)).thenReturn(Optional.of(alert));
        when(userRepository.findByUsername("analyst1")).thenReturn(Optional.of(analystUser));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        Alert closed = alertService.closeAlert(5L, "analyst1", "FALSE_POSITIVE", "Customer verified");
        assertEquals(AlertStatus.CLOSED_FALSE_POSITIVE, closed.getStatus());
    }

    @Test
    void testCloseAlertEscalatedByManager() {
        Alert alert = new Alert();
        alert.setId(5L);
        alert.setStatus(AlertStatus.ESCALATED);

        when(alertRepository.findById(5L)).thenReturn(Optional.of(alert));
        when(userRepository.findByUsername("manager1")).thenReturn(Optional.of(managerUser));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        Alert closed = alertService.closeAlert(5L, "manager1", "CONFIRMED", "Definite fraud");
        assertEquals(AlertStatus.CLOSED_CONFIRMED, closed.getStatus());
    }

    @Test
    void testCloseAlertEscalatedByAnalystThrowsAccessDenied() {
        Alert alert = new Alert();
        alert.setId(5L);
        alert.setStatus(AlertStatus.ESCALATED);

        when(alertRepository.findById(5L)).thenReturn(Optional.of(alert));
        when(userRepository.findByUsername("analyst1")).thenReturn(Optional.of(analystUser));

        assertThrows(AccessDeniedException.class, () -> alertService.closeAlert(5L, "analyst1", "CONFIRMED", ""));
    }

    @Test
    void testCloseAlertInvalidStatusConflict() {
        Alert alert = new Alert();
        alert.setId(5L);
        alert.setStatus(AlertStatus.OPEN);

        when(alertRepository.findById(5L)).thenReturn(Optional.of(alert));
        when(userRepository.findByUsername("analyst1")).thenReturn(Optional.of(analystUser));

        assertThrows(ConflictException.class, () -> alertService.closeAlert(5L, "analyst1", "CONFIRMED", ""));
    }

    @Test
    void testGetAlertFoundAndNotFound() {
        Alert alert = new Alert();
        alert.setId(1L);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.findById(2L)).thenReturn(Optional.empty());

        assertEquals(alert, alertService.getAlert(1L));
        assertThrows(ResourceNotFoundException.class, () -> alertService.getAlert(2L));
    }

    @Test
    void testListAlertsManagerAndAnalyst() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Alert> page = new PageImpl<>(List.of(new Alert()));

        when(userRepository.findByUsername("manager1")).thenReturn(Optional.of(managerUser));
        when(alertRepository.findAllFiltered(AlertStatus.OPEN, AlertSeverity.HIGH, pageable)).thenReturn(page);

        Page<Alert> mgrResult = alertService.listAlerts(AlertStatus.OPEN, AlertSeverity.HIGH, "manager1", pageable);
        assertEquals(1, mgrResult.getTotalElements());

        when(userRepository.findByUsername("analyst1")).thenReturn(Optional.of(analystUser));
        when(alertRepository.findForAnalyst(AlertStatus.OPEN, AlertSeverity.HIGH, 1L, pageable)).thenReturn(page);

        Page<Alert> anResult = alertService.listAlerts(AlertStatus.OPEN, AlertSeverity.HIGH, "analyst1", pageable);
        assertEquals(1, anResult.getTotalElements());
    }
}
