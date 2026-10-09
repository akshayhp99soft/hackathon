package com.lyxor.persistence.controller;

import com.lyxor.persistence.model.LedgerAuditSnapshot;
import com.lyxor.persistence.model.LedgerTransactionEntity;
import com.lyxor.persistence.repository.AccountRepository;
import com.lyxor.persistence.repository.LedgerTransactionRepository;
import com.lyxor.persistence.service.AccountTransferService;
import com.lyxor.persistence.service.TransactionAuditService;
import com.lyxor.persistence.service.TransactionPartitionRouter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final AccountTransferService transferService;
    private final TransactionAuditService auditService;
    private final TransactionPartitionRouter partitionRouter;

    public LedgerController(
            AccountRepository accountRepository,
            LedgerTransactionRepository transactionRepository,
            AccountTransferService transferService,
            TransactionAuditService auditService,
            TransactionPartitionRouter partitionRouter) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transferService = transferService;
        this.auditService = auditService;
        this.partitionRouter = partitionRouter;
    }

    public record TransferRequest(
            @NotBlank String tenantId,
            @NotBlank String sourceAccountId,
            @NotBlank String targetAccountId,
            @NotNull @Positive BigDecimal amount
    ) {}

    public record AuditRequest(
            @NotBlank String transactionId,
            @NotBlank String auditorNote
    ) {}

    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transferFunds(@RequestBody TransferRequest request) {
        try {
            LedgerTransactionEntity transaction = transferService.executeTransfer(
                    request.tenantId(),
                    request.sourceAccountId(),
                    request.targetAccountId(),
                    request.amount()
            );
            return ResponseEntity.ok(Map.of(
                    "transactionId", transaction.getTransactionId(),
                    "status", transaction.getStatus().name(),
                    "partition", partitionRouter.resolvePartition(request.tenantId())
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/audit")
    public ResponseEntity<Map<String, Object>> auditTransaction(@RequestBody AuditRequest request) {
        boolean success = auditService.markTransactionAudited(request.transactionId(), request.auditorNote());
        if (!success) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Transaction not found"));
        }
        return ResponseEntity.ok(Map.of("status", "AUDITED", "transactionId", request.transactionId()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<LedgerTransactionEntity>> getTransactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<LedgerTransactionEntity> all = transactionRepository.findAll();
        int offset = (page - 1) * size;
        if (offset < 0 || offset >= all.size()) {
            return ResponseEntity.ok(List.of());
        }
        int toIndex = Math.min(offset + size, all.size());
        return ResponseEntity.ok(all.subList(offset, toIndex));
    }

    @GetMapping("/snapshot")
    public ResponseEntity<LedgerAuditSnapshot> createSnapshot(@RequestParam String tenantId) {
        long count = transactionRepository.findByTenantId(tenantId).size();
        LedgerAuditSnapshot snapshot = new LedgerAuditSnapshot(UUID.randomUUID().toString(), tenantId, count);
        return ResponseEntity.ok(snapshot);
    }
}
