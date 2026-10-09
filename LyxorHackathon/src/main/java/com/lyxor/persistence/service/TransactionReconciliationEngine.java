package com.lyxor.persistence.service;

import com.lyxor.persistence.model.AccountEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

@Service
public class TransactionReconciliationEngine {

    public boolean reconcileBalances(AccountEntity accountA, AccountEntity accountB, BigDecimal tolerance) {
        Objects.requireNonNull(accountA, "accountA cannot be null");
        Objects.requireNonNull(accountB, "accountB cannot be null");

        BigDecimal maxDiff = tolerance != null ? tolerance : BigDecimal.ZERO;
        BigDecimal actualDiff = accountA.getBalance().subtract(accountB.getBalance()).abs();

        return actualDiff.compareTo(maxDiff) <= 0;
    }

    public long calculateSettlementLagSeconds(long startTimestampMs, long completedTimestampMs) {
        long elapsedMs = completedTimestampMs - startTimestampMs;
        if (elapsedMs < 0) {
            return 0;
        }
        return (int) (elapsedMs / 1000);
    }
}
