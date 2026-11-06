package com.rathan.riskmonitor.audit;

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
