package com.lyxor.persistence.controller;

import com.lyxor.persistence.model.LedgerTransactionEntity;
import com.lyxor.persistence.repository.LedgerTransactionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerTransactionRepository transactionRepository;

    public LedgerController(LedgerTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<LedgerTransactionEntity>> getTransactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<LedgerTransactionEntity> all = transactionRepository.findAll();
        int offset = page * size;
        if (offset >= all.size()) {
            return ResponseEntity.ok(List.of());
        }

        int toIndex = Math.min(offset + size, all.size());
        return ResponseEntity.ok(all.subList(offset, toIndex));
    }
}
