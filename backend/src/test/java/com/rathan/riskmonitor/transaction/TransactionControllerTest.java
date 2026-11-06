package com.rathan.riskmonitor.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rathan.riskmonitor.transaction.dto.BulkTransactionResponse;
import com.rathan.riskmonitor.transaction.dto.TransactionRequest;
import com.rathan.riskmonitor.transaction.dto.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    @Test
    @WithMockUser(username = "analyst1", roles = {"ANALYST"})
    void testIngestTransaction() throws Exception {
        TransactionRequest request = new TransactionRequest();
        request.setAccountId(1L);
        request.setMerchantId(1L);
        request.setDeviceId(1L);
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setCurrency("USD");
        request.setCountry("US");
        request.setTxnTime(Instant.now());
        request.setChannel("WEB");

        TransactionResponse response = new TransactionResponse();
        response.setId(500L);
        response.setAccountId(1L);
        response.setAmount(BigDecimal.valueOf(100.00));

        when(transactionService.ingestTransaction(any())).thenReturn(response);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(500L));
    }

    @Test
    @WithMockUser(username = "analyst1", roles = {"ANALYST"})
    void testGetTransaction() throws Exception {
        TransactionResponse response = new TransactionResponse();
        response.setId(123L);
        response.setAccountId(1L);

        when(transactionService.getTransaction(123L)).thenReturn(response);

        mockMvc.perform(get("/api/transactions/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(123L));
    }

    @Test
    @WithMockUser(username = "analyst1", roles = {"ANALYST"})
    void testIngestBulk() throws Exception {
        TransactionRequest request = new TransactionRequest();
        request.setAccountId(1L);
        request.setMerchantId(1L);
        request.setDeviceId(1L);
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setCurrency("USD");
        request.setCountry("US");
        request.setTxnTime(Instant.now());
        request.setChannel("WEB");

        when(transactionService.ingestBulk(any())).thenReturn(new BulkTransactionResponse(1, 0));

        mockMvc.perform(post("/api/transactions/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingestedCount").value(1));
    }

    @Test
    @WithMockUser(username = "manager1", roles = {"MANAGER"})
    void testReplayEndpoint() throws Exception {
        when(transactionService.replayAllTransactions()).thenReturn(Map.of(
                "totalTransactions", 100,
                "alertsGenerated", 5,
                "executionTimeMs", 120
        ));

        mockMvc.perform(post("/api/admin/replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(100))
                .andExpect(jsonPath("$.alertsGenerated").value(5));
    }
}
