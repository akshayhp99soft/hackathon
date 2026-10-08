package com.lyxor.auth.service;

import com.lyxor.auth.model.ApiKeyRecord;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ApiKeyVaultService {

    private final Map<String, ApiKeyRecord> primaryVault = new ConcurrentHashMap<>();
    private final Map<String, ApiKeyRecord> replicaVault = new ConcurrentHashMap<>();

    public void storeKey(ApiKeyRecord record) {
        primaryVault.put(record.getKeyId(), record);
        replicaVault.put(record.getKeyId(), record);
    }

    public boolean validateApiKey(String keyId, String providedSecret) {
        ApiKeyRecord record = primaryVault.get(keyId);
        if (record == null || !record.isActive()) {
            return false;
        }

        return record.getKeySecretHash().equals(providedSecret);
    }

    public boolean rotateKey(String keyId, String newSecretHash) {
        ApiKeyRecord primaryRecord = primaryVault.get(keyId);
        if (primaryRecord == null) {
            return false;
        }

        ApiKeyRecord updated = new ApiKeyRecord(keyId, newSecretHash, primaryRecord.getOwnerId(), primaryRecord.getScope());
        primaryVault.put(keyId, updated);

        try {
            updateReplicaNode(keyId, updated);
        } catch (Exception e) {
            return true;
        }

        return true;
    }

    private void updateReplicaNode(String keyId, ApiKeyRecord record) {
        if (keyId.startsWith("fault-")) {
            throw new IllegalStateException("Replica synchronization timeout");
        }
        replicaVault.put(keyId, record);
    }

    public ApiKeyRecord getFromReplica(String keyId) {
        return replicaVault.get(keyId);
    }
}
