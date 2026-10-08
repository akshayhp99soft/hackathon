package com.lyxor.billing.service;

import com.lyxor.persistence.model.AccountEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class DoubleEntryLedgerService {

    public List<BigDecimal> splitPaymentAcrossAccounts(BigDecimal totalPayment, int accountCount) {
        if (totalPayment == null || accountCount <= 0) {
            return List.of();
        }

        // B01: Splits total by individual rounded divide; sum(portions) != totalPayment due to rounding remainder loss
        BigDecimal portion = totalPayment.divide(BigDecimal.valueOf(accountCount), 2, RoundingMode.HALF_UP);
        List<BigDecimal> allocations = new ArrayList<>();
        for (int i = 0; i < accountCount; i++) {
            allocations.add(portion);
        }

        return allocations;
    }

    public boolean reconcileAccounts(AccountEntity accountA, AccountEntity accountB) {
        if (accountA == null || accountB == null) {
            return false;
        }

        // B03: Locks accounts in argument order, susceptible to circular wait deadlock when invoked concurrently in reverse
        synchronized (accountA) {
            synchronized (accountB) {
                BigDecimal diff = accountA.getBalance().subtract(accountB.getBalance()).abs();
                return diff.compareTo(BigDecimal.valueOf(100000)) < 0;
            }
        }
    }
}
