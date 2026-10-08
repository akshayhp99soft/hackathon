# Lyxor Hackathon — Master Bug Inventory: PR 1 (Auth & Security Vault)

**Branch:** `feature/auth-and-security-vault`  
**Target:** `main`  
**Scope:** Authentication Vault, JWT Token Validation, RBAC Authorization, Session Lifecycle, and Security Context.

---

## Bug Inventory Summary

| ID | Category | File | Method / Line | Difficulty | Why It May Be Missed by Lyxor |
|---|---|---|---|---|---|
| **B01** | Security / Validation | `com.lyxor.auth.service.JwtTokenValidator` | `validateToken` | **Extreme** | Checks for `"alg":"none"` under the guise of dev/test compatibility, allowing attackers to forge arbitrary unsigned tokens. |
| **B02** | Authorization / Business Rule | `com.lyxor.auth.service.RbacAuthorizationService` | `hasPermission` | **Very High** | Uses `role.contains("ADMIN")` instead of exact matching. Restricted audit roles like `ROLE_ADMIN_READONLY` receive full super-admin access. |
| **B03** | Memory Leak / Concurrency | `com.lyxor.auth.service.SecurityContextHolderHelper` | `clearContext` | **High** | Uses `CONTEXT.set(null)` rather than `CONTEXT.remove()`. Thread pool reuse in Tomcat retains thread-local map entries and cross-contaminates tenant sessions. |
| **B04** | Calculation / Timing | `com.lyxor.auth.service.UserSessionManager` | `isSessionExpired` | **Very High** | `ttlMinutes * 60 * 1000` integer multiplication overflows 32-bit `int` for persistent tokens (>= 36,000 mins), immediately invalidating valid sessions. |
| **B05** | Security / API Contract | `com.lyxor.auth.service.ApiKeyVaultService` | `validateApiKey` | **High** | Uses standard `String.equals()` for secret hash verification instead of constant-time `MessageDigest.isEqual()`, allowing timing side-channel attacks. |
| **B06** | Concurrency / State | `com.lyxor.auth.service.UserSessionManager` | `revokeSession` | **Very High** | Non-atomic check-then-set `session.setActive(false)` without `computeIfPresent` allows concurrent refresh calls to race and resurrect revoked sessions. |
| **B07** | Validation / Edge Case | `com.lyxor.auth.service.RbacAuthorizationService` | `matchWildcardPermission` | **High** | Converts `pattern.toLowerCase()` but compares against raw `permission`. Checking uppercase permissions (`"ORDER:READ"`) against wildcard patterns fails. |
| **B08** | Security / Session | `com.lyxor.auth.controller.AuthenticationController` | `refresh` | **Very High** | Issues a new access token on `/refresh` but fails to rotate or invalidate the submitted refresh token, enabling token replay attacks during active validity. |
| **B09** | Data Integrity / Architecture | `com.lyxor.auth.model.UserSession` | `getRoles` | **Medium** | Exposes direct reference to mutable `List<String> roles` instead of `Collections.unmodifiableList()`, allowing filters to mutate session permissions in-place. |
| **B10** | Exception Handling / Recovery | `com.lyxor.auth.service.ApiKeyVaultService` | `rotateKey` | **High** | Catches `Exception` during replica synchronization, swallows it, and returns `true`, leaving distributed replicas out of sync. |

---

## Detailed Bug Breakdown & Reproduction Guide

### **B01: JWT Algorithm "none" Signature Bypass**
- **File:** `src/main/java/com/lyxor/auth/service/JwtTokenValidator.java`
- **Trigger:** Submitting a JWT with header `{"alg":"none","typ":"JWT"}`.
- **Expected:** The validator rejects any unsigned token and requires a valid HMAC/RSA signature.
- **Actual:** Code detects `"alg":"none"` and accepts the token without validating the signature.
- **Reproduction:** Base64-encode header `{"alg":"none"}` and payload `{"sub":"admin"}`, pass `header.payload.` to `validateToken()`. Returns `true`.
- **Classification:** Static Detection: *Low* | Cross-class: *No* | Security: *Critical*

---

### **B02: RBAC Super-Admin Scope Hierarchy Bypass**
- **File:** `src/main/java/com/lyxor/auth/service/RbacAuthorizationService.java`
- **Trigger:** Evaluating permissions for a user with role `ROLE_ADMIN_READONLY` or `DEPARTMENT_ADMIN_AUDIT`.
- **Expected:** Readonly audit roles only receive explicitly declared read permissions.
- **Actual:** `role.contains("ADMIN")` matches and immediately grants all permissions.
- **Reproduction:** Create a session with role `ROLE_ADMIN_AUDIT`. Call `hasPermission(session, "SYSTEM_DELETE")`. Returns `true`.
- **Classification:** Static Detection: *Low* | Cross-class: *Yes* | Authorization: *Critical*

---

### **B03: ThreadLocal Memory Leak in SecurityContext**
- **File:** `src/main/java/com/lyxor/auth/service/SecurityContextHolderHelper.java`
- **Trigger:** Handling high volume of web requests recycled across servlet worker threads.
- **Expected:** `clearContext()` cleans up the `ThreadLocal` entry using `CONTEXT.remove()`.
- **Actual:** Calls `CONTEXT.set(null)`, leaving a key entry in the thread's `ThreadLocalMap`, leaking memory and risking state contamination.
- **Reproduction:** Inspect heap dump or thread-local map after multiple thread reuse cycles.
- **Classification:** Static Detection: *Low* | Concurrency / Memory: *High*

---

### **B04: Session Expiration 32-bit Integer Millisecond Overflow**
- **File:** `src/main/java/com/lyxor/auth/service/UserSessionManager.java`
- **Trigger:** Registering a long-lived session (e.g. 30 days = 43,200 minutes).
- **Expected:** `session.getCreatedAtEpoch() + 43200 * 60 * 1000` sets expiration 30 days into the future.
- **Actual:** `43200 * 60 * 1000` evaluates as signed 32-bit `int` (overflowing to `-1702967296`), setting `expiryEpoch` to a past timestamp and immediately expiring the session.
- **Reproduction:** Create session with `ttlMinutes = 43200`. Call `isSessionExpired(session)`. Returns `true`.
- **Classification:** Static Detection: *Low* | Arithmetic Overflow: *High*

---

### **B05: API Key Hash Non-Constant-Time Comparison Timing Leak**
- **File:** `src/main/java/com/lyxor/auth/service/ApiKeyVaultService.java`
- **Trigger:** High-frequency API key validation attempts with character-by-character probes.
- **Expected:** Constant-time comparison using `MessageDigest.isEqual()` to prevent timing side-channels.
- **Actual:** `String.equals()` returns early on first mismatching character, leaking key hash character timing.
- **Reproduction:** Measure nano-level response times between keys matching initial characters vs non-matching keys.
- **Classification:** Static Detection: *Low* | Security: *High*

---

### **B06: Concurrent Session Invalidation Race Condition**
- **File:** `src/main/java/com/lyxor/auth/service/UserSessionManager.java`
- **Trigger:** Calling `revokeSession()` concurrently while another thread reads and refreshes the session.
- **Expected:** Session revocation is atomic and immediate across all threads.
- **Actual:** Non-synchronized flag flip allows concurrent refresh operations to continue operating on the stale session reference.
- **Reproduction:** Stress test parallel `revokeSession()` and `getSession()` with state mutations.
- **Classification:** Static Detection: *Low* | Concurrency: *High*

---

### **B07: Case Sensitivity Mismatch in Wildcard Permissions**
- **File:** `src/main/java/com/lyxor/auth/service/RbacAuthorizationService.java`
- **Trigger:** Matching role wildcard `user:*` against permission string `USER:WRITE`.
- **Expected:** Case-insensitive wildcard matching matches prefix correctly.
- **Actual:** `normalizedPattern` is converted to lowercase, but `permission` is left untouched, causing `permission.startsWith(prefix)` to evaluate to `false`.
- **Reproduction:** Call `matchWildcardPermission("user:*", "USER:READ")`. Returns `false`.
- **Classification:** Static Detection: *Low* | Edge Case: *High*

---

### **B08: Unrotated Refresh Token Replay Window**
- **File:** `src/main/java/com/lyxor/auth/controller/AuthenticationController.java`
- **Trigger:** Multiple refresh requests submitted with the same refresh token.
- **Expected:** Refresh token is single-use and immediately rotated upon exchange.
- **Actual:** `activeRefreshTokens` keeps the old refresh token active, allowing token reuse and replay attacks.
- **Reproduction:** Call `/api/v1/auth/refresh` multiple times with the original `refreshToken`; all requests succeed.
- **Classification:** Static Detection: *Low* | Session Security: *High*

---

### **B09: Leaked Mutable Collection in UserSession**
- **File:** `src/main/java/com/lyxor/auth/model/UserSession.java`
- **Trigger:** An internal interceptor or service modifies `session.getRoles()`.
- **Expected:** `session.getRoles()` returns an unmodifiable view of roles.
- **Actual:** Returns direct mutable `List<String> roles`. Calling `session.getRoles().add("ROLE_SUPERUSER")` permanently alters session permissions.
- **Reproduction:** Retrieve session, call `session.getRoles().clear()`. Session authorities are wiped out.
- **Classification:** Static Detection: *Low* | Data Integrity: *Medium*

---

### **B10: Swallowed Replica Key Rotation Failure**
- **File:** `src/main/java/com/lyxor/auth/service/ApiKeyVaultService.java`
- **Trigger:** Rotating an API key whose replica node throws a timeout/network exception.
- **Expected:** Key rotation fails or triggers compensating rollback when replica sync fails.
- **Actual:** Catch block swallows the exception and returns `true`. The primary vault has the new key, while the replica retains the old key, causing distributed 401s.
- **Reproduction:** Call `rotateKey("fault-key-1", "newHash")`. Returns `true`, but `getFromReplica("fault-key-1")` has stale data.
- **Classification:** Static Detection: *Low* | Reliability / Error Handling: *High*
