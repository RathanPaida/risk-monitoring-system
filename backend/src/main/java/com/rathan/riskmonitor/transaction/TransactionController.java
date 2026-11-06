package com.rathan.riskmonitor.transaction;

import com.rathan.riskmonitor.transaction.dto.BulkTransactionResponse;
import com.rathan.riskmonitor.transaction.dto.TransactionRequest;
import com.rathan.riskmonitor.transaction.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Transactions", description = "Transaction ingestion, inspection, and rule replay")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER')")
    @Operation(summary = "Ingest a single transaction and trigger fraud rules")
    public ResponseEntity<TransactionResponse> ingestTransaction(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.ingestTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/transactions/bulk")
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER')")
    @Operation(summary = "Ingest a batch of transactions and trigger fraud rules")
    public ResponseEntity<BulkTransactionResponse> ingestBulk(@Valid @RequestBody List<TransactionRequest> requests) {
        BulkTransactionResponse response = transactionService.ingestBulk(requests);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER')")
    @Operation(summary = "Get transaction details by ID")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable Long id) {
        TransactionResponse response = transactionService.getTransaction(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/replay")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Replay all historical transactions through the rule engine", description = "Manager only.")
    public ResponseEntity<Map<String, Object>> replayAll() {
        Map<String, Object> result = transactionService.replayAllTransactions();
        return ResponseEntity.ok(result);
    }
}
