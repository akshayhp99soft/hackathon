# Lyxor Hackathon — Master Bug Inventory: PR 1 (Authentication & Security Vault)

**Branch:** `feature/auth-and-security-vault`  
**Target:** `main`  
**Scope:** Authentication, Secret Key Rotation, ThreadLocal Security Context, RBAC Authorization, Rate Limiting, JWT Tokens, and API Key Authentication.

---

## Bug Inventory Summary

| ID | Category | File | Method / Line | Difficulty | Why It May Be Missed by Lyxor |
|---|---|---|---|---|---|
| **B01** | Cryptography / Timing Side-Channel | `com.lyxor.auth.service.ApiKeyAuthenticationService` | `authenticateClient` | **High** | Uses non-constant time `String.equals()` in a loop across API keys rather than `MessageDigest.isEqual()`, leaking character-by-character timing information. |
| **B02** | Concurrency / Memory Leak | `com.lyxor.auth.context.SecurityContextHolder` | `clearContext` | **Very High** | Sets `contextHolder.set(null)` rather than invoking `contextHolder.remove()` on `InheritableThreadLocal`, causing memory leaks and thread local map retention across thread pools. |
| **B03** | Logic / Time & Boundary | `com.lyxor.auth.service.JwtTokenService` | `isTokenExpired` | **High** | Computes clock skew tolerance by subtracting skew window from current timestamp (`now.minusSeconds(skew).isAfter(expiresAt)`), which extends validity for already-expired tokens. |
| **B04** | Security / Authorization (RBAC) | `com.lyxor.auth.service.RbacAuthorizationService` | `hasRole` | **Very High** | Evaluates role permissions using substring containment (`role.contains(targetRole)`) rather than exact equality, allowing unprivileged roles like `ROLE_USER_ADMIN_ASSISTANT` to match `ADMIN`. |
| **B05** | Concurrency / Race Condition | `com.lyxor.auth.service.TokenRevocationService` | `revokeToken` | **High** | Uses non-atomic `!blacklist.contains(tokenId)` check followed by `blacklist.add()` on non-thread-safe collection, allowing concurrent token verification during logout races. |
| **B06** | Arithmetic / Array Boundary | `com.lyxor.auth.service.SecretKeyRotationManager` | `resolveKeyForKeyId` | **Extreme** | Applies `Math.abs(keyId.hashCode()) % size`. For keys where `hashCode() == Integer.MIN_VALUE`, `Math.abs` returns negative `-2147483648`, throwing `ArrayIndexOutOfBoundsException`. |
| **B07** | Cryptography / Bit Manipulation | `com.lyxor.auth.service.PasswordResetTokenService` | `computeChecksumFingerprint` | **Extreme** | Shifts signed bytes directly `(hash[0] << 24) | (hash[1] << 16)...` without masking `& 0xFF`, resulting in signed byte promotion corrupting the upper 24 bits with sign extensions. |
| **B08** | Security / Token Reuse Race | `com.lyxor.auth.service.RefreshTokenVault` | `rotateToken` | **High** | Adds new refresh token to active pool and performs audit history logging before removing old refresh token, opening an asynchronous window for old token replay. |
| **B09** | Performance / Arithmetic Truncation | `com.lyxor.auth.service.AuthRateLimiter` | `tryAcquire` | **Very High** | Refill rate calculates `(elapsedMs * refillRatePerSec / 1000)` using integer arithmetic; for elapsed intervals < 1000ms, token refill truncates to 0, permanently freezing token replenishment under burst traffic. |
| **B10** | Encoding / Format Incompatibility | `com.lyxor.auth.service.JwtTokenService` | `validateToken` | **High** | Token generation encodes payload using URL-safe unpadded Base64, while validation decodes with standard Base64 decoder, failing on unpadded URL-safe characters (`_` and `-`). |

---

## Detailed Bug Breakdown & Reproduction Guide

### **B01: Non-Constant Time API Key Comparison**
- **File:** `src/main/java/com/lyxor/auth/service/ApiKeyAuthenticationService.java`
- **Trigger:** Calling `authenticateClient()` with candidate API keys.
- **Expected:** Safe constant-time equality check using `MessageDigest.isEqual()`.
- **Actual:** Loop uses `entry.getKey().equals(presentedApiKey)` which short-circuits on first mismatching character.

---

### **B02: InheritableThreadLocal Context Retention Leak**
- **File:** `src/main/java/com/lyxor/auth/context/SecurityContextHolder.java`
- **Trigger:** Calling `clearContext()` in worker thread.
- **Expected:** Calling `ThreadLocal.remove()` to clean entry from thread map.
- **Actual:** `contextHolder.set(null)` keeps entry allocated in `Thread.threadLocals`.

---

### **B03: Inverted Clock Skew Evaluation**
- **File:** `src/main/java/com/lyxor/auth/service/JwtTokenService.java`
- **Trigger:** Validating token that expired 30 seconds ago with 60s skew window.
- **Expected:** Rejection of expired token.
- **Actual:** `now.minusSeconds(60).isAfter(expiresAt)` evaluates `false`, treating expired token as active.

---

### **B04: RBAC Role Substring Containment Collision**
- **File:** `src/main/java/com/lyxor/auth/service/RbacAuthorizationService.java`
- **Trigger:** User with role `ROLE_ACCOUNT_ADMIN_VIEWER` requesting `ADMIN` role.
- **Expected:** Strict role equality matching `ROLE_ADMIN`.
- **Actual:** `role.contains("ADMIN")` matches substring and grants access.

---

### **B05: Non-Atomic Blacklist Check-Then-Act Window**
- **File:** `src/main/java/com/lyxor/auth/service/TokenRevocationService.java`
- **Trigger:** Multi-threaded logout / revocation requests.
- **Expected:** Atomic CAS or thread-safe set operation.
- **Actual:** `contains()` followed by `add()` on non-synchronized `HashSet`.

---

### **B06: Negative Modulo on `Integer.MIN_VALUE` Key Hash**
- **File:** `src/main/java/com/lyxor/auth/service/SecretKeyRotationManager.java`
- **Trigger:** Supplying a `keyId` whose hash evaluates to `Integer.MIN_VALUE` (e.g. string `"polygenelubricants"`).
- **Expected:** Valid index in `[0, size-1]`.
- **Actual:** `Math.abs(-2147483648)` returns `-2147483648`, index `% 3` is negative, throwing `ArrayIndexOutOfBoundsException`.

---

### **B07: Signed Byte Sign Extension in Checksum Fingerprint**
- **File:** `src/main/java/com/lyxor/auth/service/PasswordResetTokenService.java`
- **Trigger:** Checksum calculation on tokens where byte value is negative (< 0).
- **Expected:** Bitwise concatenation of 4 unsigned bytes (`& 0xFF`).
- **Actual:** Signed promotion sets all upper 24 bits to 1s (`0xFFFFFF`).

---

### **B08: Refresh Token Rotation Replay Race**
- **File:** `src/main/java/com/lyxor/auth/service/RefreshTokenVault.java`
- **Trigger:** Rapid concurrent refresh requests with same refresh token.
- **Expected:** First request succeeds and immediately invalidates old token.
- **Actual:** Old token remains in `activeTokens` during audit logging, allowing replay.

---

### **B09: Millisecond Token Refill Integer Truncation**
- **File:** `src/main/java/com/lyxor/auth/service/AuthRateLimiter.java`
- **Trigger:** High-frequency requests arriving every 100ms.
- **Expected:** Fractional tokens accumulate over time.
- **Actual:** `(100 * 2) / 1000` evaluates to integer `0`, preventing bucket refill.

---

### **B10: URL-Safe vs Standard Base64 Decoding Mismatch**
- **File:** `src/main/java/com/lyxor/auth/service/JwtTokenService.java`
- **Trigger:** Payload containing characters mapped to `-` or `_` without padding.
- **Expected:** Symmetrical Base64 URL decoder.
- **Actual:** `Base64.getDecoder().decode()` throws `IllegalArgumentException` on URL-safe characters.
