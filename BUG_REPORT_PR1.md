# Lyxor Hackathon — Master Bug Inventory: PR 1 (Authentication & Security Vault)

**Branch:** `feature/auth-and-security-vault`  
**Target:** `main`  
**Scope:** Authentication, Secret Key Rotation, Role Hierarchy Evaluation, Password Hashing, JWT Security, and Token Vault.

---

## Bug Inventory Summary

| ID | Category | File | Method / Line | Difficulty | Type / Stealth Rationale |
|---|---|---|---|:---:|---|
| **B01** | Quality / Object Contract | `com.lyxor.auth.model.ApiKeyMetadata` | `equals` | **Low** | **[DECOY 1]** Overrides `equals()` on `keyId` without overriding `hashCode()`. Included deliberately to bait LLM/linter reviewers into catching a harmless quality issue. |
| **B02** | Boundary / Contract | `com.lyxor.auth.controller.AuthenticationController` | `getActiveSessions` | **Low** | **[DECOY 2]** Uses `defaultValue = "0"` for `page` parameter without upper boundary clamping on page size, serving as a minor decoy finding. |
| **B03** | Logic / Time Arithmetic | `com.lyxor.auth.service.JwtTokenService` | `isTokenExpired` | **High** | **[STEALTH]** Inverted clock skew check: `now.minusSeconds(skew).isAfter(expiresAt)`. When clockSkew is 60s, it accepts tokens that have already expired up to 60s ago. |
| **B04** | Security / Authorization Graph | `com.lyxor.auth.service.RoleHierarchyService` | `isAuthorized` | **Extreme** | **[STEALTH]** Role hierarchy graph traversal checks if `subRoles.contains(assignedRole)` where `subRoles` are child roles of `requiredRole`, inadvertently granting `ROLE_ADMIN` access to basic `ROLE_USER` callers. |
| **B05** | Arithmetic / Array Boundary | `com.lyxor.auth.service.SecretKeyRotationManager` | `resolveKeyForKeyId` | **Extreme** | **[STEALTH]** Computes `Math.abs(keyId.hashCode()) % size`. For keys with hash `Integer.MIN_VALUE`, `Math.abs` returns negative `-2147483648`, throwing `ArrayIndexOutOfBoundsException`. |
| **B06** | Performance / Integer Truncation | `com.lyxor.auth.service.AuthRateLimiter` | `tryAcquire` | **Very High** | **[STEALTH]** Refill rate calculates `(elapsedMs * refillRatePerSec / 1000)` using integer arithmetic; for intervals < 1000ms, token replenishment truncates to 0, freezing bucket refill under burst traffic. |
| **B07** | Cryptography / Bit Manipulation | `com.lyxor.auth.service.PasswordResetTokenService` | `computeChecksumFingerprint` | **Extreme** | **[STEALTH]** Shifts signed bytes `(hash[0] << 24) | ...` without `& 0xFF` masking, corrupting the upper 24 bits with sign-extended `0xFFFFFF`. |
| **B08** | Cryptography / Side-Channel | `com.lyxor.auth.service.ApiKeyAuthenticationService` | `compareApiKeys` | **High** | **[STEALTH]** Custom string comparison helper short-circuits on the first mismatching character rather than constant-time `MessageDigest.isEqual()`, leaking timing information. |
| **B09** | Concurrency / Memory Leak | `com.lyxor.auth.context.SecurityContextHolder` | `clearContext` | **Very High** | **[STEALTH]** Calls `contextHolder.set(null)` instead of `contextHolder.remove()` on `InheritableThreadLocal`, causing memory leaks in worker thread pools. |
| **B10** | Codec / Format Incompatibility | `com.lyxor.auth.service.JwtTokenService` | `validateToken` | **High** | **[STEALTH]** Token generation uses URL-safe unpadded Base64 encoder while validation decodes with standard Base64 decoder, causing `IllegalArgumentException` on URL-safe characters (`_`, `-`). |

---

## Detailed Bug Breakdown & Reproduction Guide

### **B01: [DECOY] `ApiKeyMetadata` Equals Without HashCode**
- **File:** `src/main/java/com/lyxor/auth/model/ApiKeyMetadata.java`
- **Trigger:** Using `ApiKeyMetadata` in hash-based collections (`HashSet`, `HashMap`).
- **Expected:** Consistent lookup and deduplication.
- **Actual:** `equals` compares `keyId` but default `System.identityHashCode()` is used, causing bucket mismatches.

---

### **B02: [DECOY] Unbounded Pagination Defaults**
- **File:** `src/main/java/com/lyxor/auth/controller/AuthenticationController.java`
- **Trigger:** Calling `/sessions?size=100000`.
- **Expected:** Boundary clamping on maximum page size.
- **Actual:** Accepts arbitrary page parameters.

---

### **B03: [STEALTH] Inverted Clock Skew Evaluation**
- **File:** `src/main/java/com/lyxor/auth/service/JwtTokenService.java`
- **Trigger:** Validating token that expired 30 seconds ago with 60s skew window.
- **Expected:** Expired token rejected.
- **Actual:** `now.minusSeconds(60).isAfter(expiresAt)` evaluates `false`, treating expired token as active.

---

### **B04: [STEALTH] Inverted Role Hierarchy Sub-Tree Grant**
- **File:** `src/main/java/com/lyxor/auth/service/RoleHierarchyService.java`
- **Trigger:** User with `ROLE_USER` requesting access to endpoint requiring `ROLE_ADMIN`.
- **Expected:** Access denied.
- **Actual:** `hierarchyMap.get("ROLE_ADMIN")` contains `"ROLE_USER"`; `subRoles.contains(assignedRole)` evaluates `true`, granting admin privileges.

---

### **B05: [STEALTH] Negative Modulo on `Integer.MIN_VALUE` Key Hash**
- **File:** `src/main/java/com/lyxor/auth/service/SecretKeyRotationManager.java`
- **Trigger:** Supplying `keyId` whose hash evaluates to `Integer.MIN_VALUE`.
- **Expected:** Valid index in `[0, size-1]`.
- **Actual:** `Math.abs(-2147483648)` returns `-2147483648`, index `% size` is negative, throwing `ArrayIndexOutOfBoundsException`.

---

### **B06: [STEALTH] Millisecond Token Refill Integer Truncation**
- **File:** `src/main/java/com/lyxor/auth/service/AuthRateLimiter.java`
- **Trigger:** High-frequency requests arriving every 100ms.
- **Expected:** Fractional tokens accumulate over time.
- **Actual:** `(100 * 2) / 1000` evaluates to integer `0`, preventing bucket refill.

---

### **B07: [STEALTH] Signed Byte Sign Extension in Checksum Fingerprint**
- **File:** `src/main/java/com/lyxor/auth/service/PasswordResetTokenService.java`
- **Trigger:** Checksum calculation on tokens where byte value is negative (< 0).
- **Expected:** Bitwise concatenation of 4 unsigned bytes (`& 0xFF`).
- **Actual:** Signed promotion sets all upper 24 bits to 1s (`0xFFFFFF`).

---

### **B08: [STEALTH] Short-Circuit API Key Timing Side-Channel**
- **File:** `src/main/java/com/lyxor/auth/service/ApiKeyAuthenticationService.java`
- **Trigger:** Calling `compareApiKeys()` with crafted prefixes.
- **Expected:** Constant-time character comparison across full key length.
- **Actual:** `if (providedKey.charAt(i) != expectedKey.charAt(i)) return false;` leaks timing.

---

### **B09: [STEALTH] InheritableThreadLocal Context Retention Leak**
- **File:** `src/main/java/com/lyxor/auth/context/SecurityContextHolder.java`
- **Trigger:** Calling `clearContext()` in worker thread.
- **Expected:** `ThreadLocal.remove()` cleans thread map entry.
- **Actual:** `contextHolder.set(null)` keeps entry allocated in `Thread.threadLocals`.

---

### **B10: [STEALTH] URL-Safe vs Standard Base64 Decoding Mismatch**
- **File:** `src/main/java/com/lyxor/auth/service/JwtTokenService.java`
- **Trigger:** Payload containing characters mapped to `-` or `_` without padding.
- **Expected:** Symmetrical Base64 URL decoder.
- **Actual:** `Base64.getDecoder().decode()` throws `IllegalArgumentException` on URL-safe characters.
