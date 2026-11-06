package com.rathan.riskmonitor.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rathan.riskmonitor.transaction.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AlertWorkflowSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlertService alertService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long testAlertId;

    @BeforeEach
    void setUp() {
        Transaction txn = transactionRepository.findAll().stream().findFirst().orElseGet(() -> {
            Customer cust = customerRepository.save(new Customer(9001L, "Test Cust", "US", Instant.now()));
            Account acc = accountRepository.save(new Account(9001L, cust, "CHECKING", Instant.now(), "ACTIVE"));
            Merchant merch = merchantRepository.save(new Merchant(9001L, "Test Merch", "RETAIL", "US"));
            Device dev = deviceRepository.save(new Device(9001L, cust, "hash9001", Instant.now()));
            Transaction newTxn = new Transaction();
            newTxn.setId(9001L);
            newTxn.setAccount(acc);
            newTxn.setMerchant(merch);
            newTxn.setDevice(dev);
            newTxn.setAmount(BigDecimal.valueOf(100.00));
            newTxn.setCurrency("USD");
            newTxn.setCountry("US");
            newTxn.setTxnTime(Instant.now());
            newTxn.setChannel("WEB");
            newTxn.setCreatedAt(Instant.now());
            return transactionRepository.save(newTxn);
        });
        Alert alert = alertService.createAlert(txn, "TestRule", AlertSeverity.HIGH, "Test initial alert");
        testAlertId = alert.getId();
    }

    @Test
    @DisplayName("Security: Unauthenticated request returns 401 Unauthorized")
    void unauthenticatedAccess_returns401() throws Exception {
        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RBAC: Analyst calling /api/audit returns 403 Forbidden")
    void analystAccessToAuditLog_returns403() throws Exception {
        mockMvc.perform(get("/api/audit")
                .param("alertId", testAlertId.toString())
                .with(httpBasic("analyst1", "Analyst123!")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC: Manager calling /api/audit returns 200 OK")
    void managerAccessToAuditLog_returns200() throws Exception {
        mockMvc.perform(get("/api/audit")
                .param("alertId", testAlertId.toString())
                .with(httpBasic("manager1", "Manager123!")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Workflow & State Machine: Full End-to-End Lifecycle")
    void fullAlertWorkflowLifecycle() throws Exception {
        // 1. Claim OPEN alert as analyst1 -> transitions to IN_REVIEW
        mockMvc.perform(post("/api/alerts/" + testAlertId + "/claim")
                .with(httpBasic("analyst1", "Analyst123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.assignedToUsername").value("analyst1"));

        // 2. Illegal transition: Trying to claim an alert that is already IN_REVIEW returns 409 Conflict
        mockMvc.perform(post("/api/alerts/" + testAlertId + "/claim")
                .with(httpBasic("analyst1", "Analyst123!")))
                .andExpect(status().isConflict());

        // 3. Escalate alert from IN_REVIEW to ESCALATED with a reason
        EscalateRequest escalateRequest = new EscalateRequest("Complex multi-jurisdictional wire fraud suspected");
        mockMvc.perform(post("/api/alerts/" + testAlertId + "/escalate")
                .with(httpBasic("analyst1", "Analyst123!"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(escalateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCALATED"));

        // 4. Illegal transition: Trying to escalate an already ESCALATED alert returns 409 Conflict
        mockMvc.perform(post("/api/alerts/" + testAlertId + "/escalate")
                .with(httpBasic("analyst1", "Analyst123!"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(escalateRequest)))
                .andExpect(status().isConflict());

        // 5. RBAC Enforcement: Analyst attempts to close an ESCALATED alert -> 403 Forbidden
        CloseRequest closeRequest = new CloseRequest("CONFIRMED", "Confirmed fraudulent card takeover");
        mockMvc.perform(post("/api/alerts/" + testAlertId + "/close")
                .with(httpBasic("analyst1", "Analyst123!"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(closeRequest)))
                .andExpect(status().isForbidden());

        // 6. Manager successfully closes the ESCALATED alert -> 200 OK & CLOSED_CONFIRMED
        mockMvc.perform(post("/api/alerts/" + testAlertId + "/close")
                .with(httpBasic("manager1", "Manager123!"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(closeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED_CONFIRMED"));

        // 7. Verify full audit trail via Manager audit endpoint
        mockMvc.perform(get("/api/audit")
                .param("alertId", testAlertId.toString())
                .with(httpBasic("manager1", "Manager123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("CREATED"))
                .andExpect(jsonPath("$[1].action").value("CLAIMED"))
                .andExpect(jsonPath("$[2].action").value("ESCALATED"))
                .andExpect(jsonPath("$[3].action").value("CLOSED"));
    }
}
