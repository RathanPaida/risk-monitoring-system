package com.rathan.riskmonitor.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class AuditLogImmutabilityTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log", Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO customers (id, name, home_country, created_at) VALUES (99999, 'Test Cust', 'US', NOW()) ON CONFLICT DO NOTHING");
            jdbcTemplate.update("INSERT INTO accounts (id, customer_id, account_type, opened_at, status) VALUES (99999, 99999, 'CHECKING', NOW(), 'ACTIVE') ON CONFLICT DO NOTHING");
            jdbcTemplate.update("INSERT INTO merchants (id, name, category, country) VALUES (99999, 'Test Merchant', 'RETAIL', 'US') ON CONFLICT DO NOTHING");
            jdbcTemplate.update("INSERT INTO devices (id, customer_id, device_hash, first_seen_at) VALUES (99999, 99999, 'hash99999', NOW()) ON CONFLICT DO NOTHING");
            jdbcTemplate.update("INSERT INTO transactions (id, account_id, merchant_id, device_id, amount, currency, country, txn_time, channel, created_at) " +
                    "VALUES (99999, 99999, 99999, 99999, 100.00, 'USD', 'US', NOW(), 'WEB', NOW()) ON CONFLICT DO NOTHING");
            Long alertId = jdbcTemplate.queryForObject("INSERT INTO alerts (txn_id, rule_name, severity, status, reason, created_at, updated_at) " +
                    "VALUES (99999, 'TestRule', 'HIGH', 'OPEN', 'Test reason', NOW(), NOW()) RETURNING id", Long.class);
            jdbcTemplate.update("INSERT INTO audit_log (alert_id, action, actor, old_status, new_status, details, created_at) " +
                    "VALUES (?, 'CREATED', 'system', NULL, 'OPEN', 'Initial setup', NOW())", alertId);
        }
    }

    @Test
    @DisplayName("Database trigger prohibits UPDATE on audit_log")
    void updateAuditLog_throwsExceptionFromTrigger() {
        // Query an existing audit log ID
        Long auditId = jdbcTemplate.queryForObject("SELECT id FROM audit_log LIMIT 1", Long.class);
        assertThat(auditId).isNotNull();

        DataAccessException exception = assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.update("UPDATE audit_log SET details = 'tampered_data' WHERE id = ?", auditId);
        });

        assertThat(exception.getMessage())
                .containsIgnoringCase("audit_log is immutable: UPDATE and DELETE operations are forbidden");
    }

    @Test
    @DisplayName("Database trigger prohibits DELETE on audit_log")
    void deleteAuditLog_throwsExceptionFromTrigger() {
        // Query an existing audit log ID
        Long auditId = jdbcTemplate.queryForObject("SELECT id FROM audit_log LIMIT 1", Long.class);
        assertThat(auditId).isNotNull();

        DataAccessException exception = assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.update("DELETE FROM audit_log WHERE id = ?", auditId);
        });

        assertThat(exception.getMessage())
                .containsIgnoringCase("audit_log is immutable: UPDATE and DELETE operations are forbidden");
    }
}
