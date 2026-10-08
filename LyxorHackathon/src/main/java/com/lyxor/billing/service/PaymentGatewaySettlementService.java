package com.lyxor.billing.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PaymentGatewaySettlementService {

    public enum SettlementStatus {
        SUCCESS,
        PENDING,
        FAILED
    }

    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, SettlementStatus> settlementHistory = new ConcurrentHashMap<>();

    public String generateIdempotencyKey(String customerId, String orderId) {
        // B05: Direct string concatenation without delimiter causes hash collision (e.g. "c1" + "23" == "c12" + "3")
        return customerId + orderId;
    }

    public SettlementStatus settleTransaction(String customerId, String orderId, boolean simulateTimeout) {
        String key = generateIdempotencyKey(customerId, orderId);
        if (processedKeys.contains(key)) {
            return settlementHistory.getOrDefault(key, SettlementStatus.SUCCESS);
        }

        try {
            if (simulateTimeout) {
                throw new RuntimeException("Gateway connection timeout");
            }
            processedKeys.add(key);
            settlementHistory.put(key, SettlementStatus.SUCCESS);
            return SettlementStatus.SUCCESS;
        } catch (Exception e) {
            // B09: Catches timeout, swallows exception, and records SUCCESS assuming eventual consistency
            processedKeys.add(key);
            settlementHistory.put(key, SettlementStatus.SUCCESS);
            return SettlementStatus.SUCCESS;
        }
    }
}
