# PayCore: Backend Sign-Off Assessment & Verification Report (Round 2)

**Generated At:** 2026-11-17T11:05:00Z  
**Branch:** `wp-backend-fix-2`  
**Target Candidate:** Atmakuri Yeshwanth (PayPal Software Engineer Intern Application)  
**Total Reactor Tests Run:** 248  
**Failures / Errors:** 0  
**Overall Verdict:** **PHASE 1 (CODE, SECURITY & ARCHITECTURE GATES) PASSED** :white_check_mark: | **PHASE 2 (CONTAINER BENCHMARKS & CRASH HARNESS) PENDING DOCKER ACCESS**

---

## 1. Executive Summary & Verification Matrix

| Ref | Domain / Item | Requirement & Prompt Hypotheses | Status | Evidence Path |
|---|---|---|---|---|
| **B** | **Advisory Lock & Batch Demarcation** | 1. Real PostgreSQL concurrent invocation test proving 409 conflict.<br/>2. Document transaction boundary vs remote ledger HTTP calls. | **PASS** :white_check_mark: | [BatchCutoffAdvisoryLockPostgresIntegrationTest.java](file:///mnt/nexus/paycore/services/settlement-service/src/test/java/io/github/yesh/paycore/settlement/integration/BatchCutoffAdvisoryLockPostgresIntegrationTest.java)<br/>[docs/architecture.md](file:///mnt/nexus/paycore/docs/architecture.md#L73-L85) |
| **C1/C3**| **Test Database Provenance & Isolation** | Explicit fail-fast when Testcontainers fails; no silent unmanaged fallback; clean state between test executions. | **PASS** :white_check_mark: | `BaseIntegrationTest.java` across all 3 services<br/>`@BeforeEach` deleteAll clean isolation |
| **D1** | **CI Frontend Lint Hygiene** | ESLint enforced with zero warnings allowed (`--max-warnings 0`) in GitHub Actions CI. | **PASS** :white_check_mark: | [.github/workflows/ci.yml](file:///.github/workflows/ci.yml#L100-L102)<br/>[web/ops-console/package.json](file:///mnt/nexus/web/ops-console/package.json#L9) |
| **D2** | **Git History Secret Audit** | Automated scanner inspecting all commits across git history for private keys, tokens, and credentials. | **PASS** :white_check_mark: | [scripts/scan-git-history-secrets.py](file:///mnt/nexus/paycore/scripts/scan-git-history-secrets.py)<br/>[docs/audit/evidence/git-history-secret-scan.txt](file:///mnt/nexus/paycore/docs/audit/evidence/git-history-secret-scan.txt) |
| **E1** | **Logout Deny-List Test** | Authenticate, fetch user, logout, verify Redis TTL <= 900s, and assert revoked token gets 401 Unauthorized. | **PASS** :white_check_mark: | [LogoutDenyListIntegrationTest.java](file:///mnt/nexus/paycore/services/identity-service/src/test/java/io/github/yesh/paycore/identity/integration/LogoutDenyListIntegrationTest.java) |
| **E2** | **CORS Origin Rejection** | Test malicious/untrusted Origin in preflight OPTIONS and requests; verify no wildcard and no credentials granted. | **PASS** :white_check_mark: | [CorsRejectionSecurityTest.java (identity)](file:///mnt/nexus/paycore/services/identity-service/src/test/java/io/github/yesh/paycore/identity/security/CorsRejectionSecurityTest.java)<br/>[CorsRejectionSecurityTest.java (ledger)](file:///mnt/nexus/paycore/services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/security/CorsRejectionSecurityTest.java) |
| **E3** | **RBAC Matrix Audit & Enforcement** | Audit all Playbook Section 12 matrix rows to controllers, HTTP filters, and automated tests. | **PASS** :white_check_mark: | [docs/audit/role-matrix-audit.md](file:///mnt/nexus/paycore/docs/audit/role-matrix-audit.md)<br/>`IdentityRoleMatrixSecurityTest.java`<br/>`SettlementRoleMatrixSecurityTest.java` |
| **E4** | **Brute-Force Lockout & Token Reuse** | Real DB/Redis test for 5-attempt lockout (15 min) and refresh token replay family revocation + audit logs. | **PASS** :white_check_mark: | [AuthSecurityRealDatabaseIntegrationTest.java](file:///mnt/nexus/paycore/services/identity-service/src/test/java/io/github/yesh/paycore/identity/integration/AuthSecurityRealDatabaseIntegrationTest.java) |
| **E5** | **General JSON Request Body Limit** | 2 MB request body limit filter for all non-multipart JSON payloads returning RFC 9457 HTTP 413. | **PASS** :white_check_mark: | [RequestBodyLimitFilter.java](file:///mnt/nexus/paycore/libs/paycore-common/src/main/java/io/github/yesh/paycore/common/web/RequestBodyLimitFilter.java)<br/>`RequestBodyLimitFilterTest.java`<br/>`JsonBodyLimitSecurityTest.java` |
| **F1** | **Documentation Purge** | Remove all exploratory single-run recovery times (7,073 ms) from official reports; status marked PENDING REAL RUN. | **PASS** :white_check_mark: | [docs/crash-test-report.md](file:///mnt/nexus/paycore/docs/crash-test-report.md)<br/>[docs/audit/benchmark-provenance.md](file:///mnt/nexus/paycore/docs/audit/benchmark-provenance.md) |
| **A1/A2**| **Benchmark Inventory & Provenance** | Full audit of 24 raw benchmark files in `docs/benchmarks/raw/` classified as simulated mocks; zero active throughput claims. | **PASS** :white_check_mark: | [docs/audit/benchmark-inventory.md](file:///mnt/nexus/paycore/docs/audit/benchmark-inventory.md)<br/>[docs/audit/benchmark-provenance.md](file:///mnt/nexus/paycore/docs/audit/benchmark-provenance.md) |
| **Step 1/3/7**| **Docker Compose Stack, Crash SLA & Tier-1 Benchmarks** | Real Compose startup, 50-iteration JVM SIGKILL crash matrix, 72-cell Tier 1 benchmark suite on disk-backed volume. | **PENDING DOCKER** | Awaiting owner ACL configuration (`sudo setfacl -m u:yesh:rw /var/run/docker.sock`) |

---

## 2. In-Depth Technical Verification

### 2.1 Item B: PostgreSQL Session Advisory Lock Demarcation
- **Concurrency Test (`BatchCutoffAdvisoryLockPostgresIntegrationTest`):**
  Spawns 2 concurrent threads attempting `batchCutoffService.runCutoff()` against real PostgreSQL. The winning thread acquires `pg_try_advisory_lock(ADVISORY_LOCK_ID)` on a dedicated JDBC connection; the losing thread immediately throws `InvalidBatchStateException` ("Another batch cutoff run is currently in progress") and returns HTTP 409 without blocking.
- **Transaction & Ledger Call Architecture:**
  - *Short Staging Transaction:* The batch creation, record grouping, and initial payout staging execute in a short database transaction that commits immediately.
  - *Out-of-Transaction Remote Calls:* `LedgerClient.initiatePayout()` calls execute outside the database transaction, preventing PostgreSQL connection pool exhaustion or transaction lock timeouts during network spikes.
  - *Resilient Partial Failure:* If a ledger call fails or times out, the specific payout is marked `FAILED` and the batch transitions to `PARTIAL_FAILURE`.
  - *Deterministic Resumption:* The batch can be resumed via `/v1/batches/{batchId}/resume`, re-attempting only failed payouts with the deterministic idempotency key (`batchId:sellerId`), preventing duplicate payouts or double-spending.

### 2.2 Item E1 & E4: Identity Security, Revocation & Lockout
- **Logout Deny-List (`LogoutDenyListIntegrationTest`):**
  Issues login request, accesses `/auth/me` (200), invokes `/auth/logout`. Verifies Redis key `paycore:denylist:<jti>` exists with positive TTL <= 900s. Subsequent request with revoked JWT is rejected with HTTP 401 Unauthorized.
- **Brute-Force Lockout:**
  5 consecutive failed logins trigger account lockout for 15 minutes. 6th attempt with valid password returns HTTP 401 (`invalid_credentials`), verified via Redis key `paycore:auth:lock:<username>` and PostgreSQL `audit_logs` record `LOGIN_LOCKED`.
- **Refresh Token Replay Family Revocation (`TokenRevocationService`):**
  Implemented with `@Transactional(propagation = Propagation.REQUIRES_NEW)`. When an already-used refresh token is presented, the entire token family is immediately revoked in PostgreSQL (`revoked_at` populated across all child tokens) and an audit log (`REFRESH_TOKEN_REUSE_DETECTED`) is permanently written before returning HTTP 401.

### 2.3 Item E2 & E5: Transport Security & Payload Protections
- **CORS Origin Rejection (`CorsRejectionSecurityTest`):**
  Tested in `identity-service` and `ledger-service`. Requests from untrusted origins (`https://evil-attacker.com`) do not receive `Access-Control-Allow-Origin: *` or origin reflection, and `Access-Control-Allow-Credentials` is rejected.
- **Request Body Size Limit (`RequestBodyLimitFilter`):**
  Placed at highest filter precedence in `paycore-common`. Inspects `Content-Length` header and wraps `ServletInputStream` in a `BoundedServletInputStream` enforcing a strict 2 MB limit on non-multipart payloads. Requests exceeding 2 MB immediately abort with RFC 9457 HTTP 413 `payload_too_large`.

---

## 3. Test Suite Breakdown (248 Tests Passing)

```
[INFO] Reactor Summary for paycore-parent 1.0.0-SNAPSHOT:
[INFO] paycore-parent ..................................... SUCCESS [  0.246 s]
[INFO] paycore-common ..................................... SUCCESS [  4.693 s] (20 tests)
[INFO] identity-service ................................... SUCCESS [ 19.541 s] (54 tests)
[INFO] ledger-service ..................................... SUCCESS [ 23.910 s] (104 tests)
[INFO] settlement-service ................................. SUCCESS [ 18.068 s] (70 tests)
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS (248 tests run, 0 failures, 0 errors)
```

---

## 4. Next Actions (Phase 2 & Docker Access)

To proceed with Step 1 (Docker Compose deployment), Step 3 (50-iteration JVM SIGKILL crash matrix), and Step 7 (72-cell Tier 1 benchmark suite):
1. **Docker Access:** Owner runs `sudo setfacl -m u:yesh:rw /var/run/docker.sock`.
2. **Database Backup:** Run `pg_dump` of existing host database to preserve exploratory data.
3. **Execution:** Bring up Docker Compose stack on clean disk-backed volume (`deploy/docker-compose.yml`), execute `scripts/e2e-flow.sh` twice, and run automated crash and benchmark suites.
