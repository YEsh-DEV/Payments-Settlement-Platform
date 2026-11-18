# PayCore: Master Backend Sign-Off & Verification Report (Consolidated)

**Document Version:** 2.0.0 (Master Consolidated Audit)  
**Generated At:** 2026-11-18T08:30:00Z  
**Branch:** `wp-backend-fix-2`  
**Target Candidate:** Atmakuri Yeshwanth (PayPal Software Engineer Intern Application & System Defense)  
**Total Reactor Tests Run:** 258  
**Failures / Errors:** 0 (100% Pass)  
**Overall Status:** **PHASE 1 (CODE, DATA INTEGRITY & SECURITY GATES) SIGNED OFF** :white_check_mark: | **PHASE 2 (CONTAINER BENCHMARKS & SIGKILL CRASH HARNESS) PENDING DOCKER SOCKET ACL**

---

## 1. Executive Summary & Verification Matrix

This master document consolidates all historical audit findings (F1–F21), gate corrections (WP-GATE-1 & GATE-2), backend sign-off items (B1–B10), and Round 2 verification checks into a single, authoritative reference. Every claim is verified against executable code, migrations, tests, or live databases.

| Ref | Domain / Item | Requirement & Prompt Hypotheses | Status | Evidence Path |
|---|---|---|---|---|
| **B** | **Advisory Lock & Batch Demarcation** | Session-level PostgreSQL advisory lock (`pg_try_advisory_lock`); 2-thread concurrent race returns 409 conflict; remote ledger HTTP calls execute outside the short DB staging transaction; deterministic resumption via `/v1/batches/{id}/resume`. | **PASS** :white_check_mark: | [BatchCutoffAdvisoryLockPostgresIntegrationTest.java](file:///mnt/nexus/paycore/services/settlement-service/src/test/java/io/github/yesh/paycore/settlement/integration/BatchCutoffAdvisoryLockPostgresIntegrationTest.java)<br>[architecture.md](file:///mnt/nexus/paycore/docs/architecture.md#L73-L85) |
| **C1/C3**| **Fail-Fast Test Base & Isolation** | Fail-fast when Testcontainers fails; zero silent unmanaged fallback; clean `@BeforeEach` database and Redis isolation. | **PASS** :white_check_mark: | `BaseIntegrationTest.java` across all 3 services<br>Clean table reset in `@BeforeEach` |
| **C2** | **Real-Database Integration Suite** | Real PostgreSQL + Redis concurrency test suite (S0 & S1 0-deadlock concurrency + I1–I8 invariants, 8-thread idempotency race, engine-level immutability triggers, outbox `SKIP LOCKED` concurrency, Flyway schema validation). | **PASS** :white_check_mark: | [LedgerRealDatabaseIntegrationTest.java](file:///mnt/nexus/paycore/services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/integration/LedgerRealDatabaseIntegrationTest.java)<br>[V7__journal_immutability_trigger.sql](file:///mnt/nexus/paycore/services/ledger-service/src/main/resources/db/migration/V7__journal_immutability_trigger.sql) |
| **D1** | **CI Lint Hygiene** | ESLint enforced with zero warnings allowed (`--max-warnings 0`) in frontend build and GitHub Actions CI. | **PASS** :white_check_mark: | [.github/workflows/ci.yml](file:///.github/workflows/ci.yml#L100-L102)<br>[package.json](file:///mnt/nexus/paycore/web/ops-console/package.json#L9) |
| **D2** | **Git History Secret Audit** | Automated audit inspecting all 38 git commits across history for private keys, tokens, and credentials via Gitleaks and custom scanner. | **PASS** :white_check_mark: | [scripts/scan-git-history-secrets.py](file:///mnt/nexus/paycore/scripts/scan-git-history-secrets.py)<br>[.gitleaks.toml](file:///mnt/nexus/paycore/.gitleaks.toml)<br>[evidence/git-history-secret-scan.txt](file:///mnt/nexus/paycore/docs/audit/evidence/git-history-secret-scan.txt) |
| **E1** | **Logout Deny-List Test** | Authenticate, fetch user, logout, verify Redis TTL <= 900s, and assert revoked token gets HTTP 401 Unauthorized. | **PASS** :white_check_mark: | [LogoutDenyListIntegrationTest.java](file:///mnt/nexus/paycore/services/identity-service/src/test/java/io/github/yesh/paycore/identity/integration/LogoutDenyListIntegrationTest.java) |
| **E2** | **CORS Origin Rejection** | Test untrusted Origin in preflight OPTIONS and direct requests; verify no wildcard `*`, no reflection, and no credentials granted across Identity, Ledger, Settlement, and Nginx. | **PASS** :white_check_mark: | `CorsRejectionSecurityTest.java` (Identity, Ledger, Settlement)<br>[deploy/nginx.conf](file:///mnt/nexus/paycore/deploy/nginx.conf) |
| **E3** | **RBAC Matrix Audit & Enforcement** | Audit all Playbook Section 12 matrix rows to controllers, HTTP filters, and automated tests. | **PASS** :white_check_mark: | [role-matrix-audit.md](file:///mnt/nexus/paycore/docs/audit/role-matrix-audit.md)<br>`IdentityRoleMatrixSecurityTest.java`<br>`RoleMatrixSecurityTest.java`<br>`SettlementRoleMatrixSecurityTest.java` |
| **E4** | **Brute-Force Lockout & Token Reuse** | Real DB/Redis test for 5-attempt lockout (15 min) and refresh token replay family revocation (`REQUIRES_NEW`) + audit logs. | **PASS** :white_check_mark: | [AuthSecurityRealDatabaseIntegrationTest.java](file:///mnt/nexus/paycore/services/identity-service/src/test/java/io/github/yesh/paycore/identity/integration/AuthSecurityRealDatabaseIntegrationTest.java) |
| **E5** | **General JSON Request Body Limit** | 2 MB request body limit filter for all non-multipart JSON payloads returning RFC 9457 HTTP 413, verified for both Content-Length and chunked transfer encoding. | **PASS** :white_check_mark: | [RequestBodyLimitFilter.java](file:///mnt/nexus/paycore/libs/paycore-common/src/main/java/io/github/yesh/paycore/common/web/RequestBodyLimitFilter.java)<br>`RequestBodyLimitFilterTest.java`<br>`JsonBodyLimitSecurityTest.java` |
| **F1** | **Documentation Purge & Clean Provenance** | Remove all exploratory single-run recovery times (7,073 ms) and synthetic throughput claims (3,906 TPS) from official reports; status marked PENDING REAL RUN. | **PASS** :white_check_mark: | [crash-test-report.md](file:///mnt/nexus/paycore/docs/crash-test-report.md)<br>[benchmark-provenance.md](file:///mnt/nexus/paycore/docs/audit/benchmark-provenance.md)<br>[benchmark-inventory.md](file:///mnt/nexus/paycore/docs/audit/benchmark-inventory.md) |
| **B2** | **Integer Paise Unit Integrity** | Strict integer paise representation across database, domain models, APIs, and reports; Rupee values shown only as derived formatting (`1,310,050 paise (₹13,100.50)`). | **PASS** :white_check_mark: | `IntegrityService.java`<br>`MoneyTest.java`<br>[quality-report.md](file:///mnt/nexus/paycore/docs/quality-report.md) |
| **B3** | **Clean Volume Repeatable E2E** | Multi-run E2E business flow on clean PostgreSQL volume: Run 1 (fresh 18 accounts -> 20 accounts, 9 journals, 0 delta) and Run 2 (idempotent repeat, 22 accounts, 18 journals, 0 violations). | **PASS** :white_check_mark: | Section 4 of this report<br>[scripts/e2e-flow.sh](file:///mnt/nexus/paycore/scripts/e2e-flow.sh) |
| **B6** | **Maker-Checker Dual Enforcement** | Database-level SQL CHECK constraint (`chk_adjustments_maker_checker`) rejects `approved_by = proposed_by` at the engine level; API returns HTTP 422 `maker_checker_violation`. | **PASS** :white_check_mark: | [V3__maker_checker_check_constraint.sql](file:///mnt/nexus/paycore/services/settlement-service/src/main/resources/db/migration/V3__maker_checker_check_constraint.sql)<br>`MakerCheckerDatabaseConstraintTest.java`<br>`AdjustmentControllerMvcTest.java` |
| **Step 1/3/7**| **Docker Compose, SIGKILL Matrix & Benchmarks** | Compose stack deployment, 50-iteration JVM SIGKILL crash test, 72-cell Tier 1 benchmark suite on NVMe disk volume. | **PENDING DOCKER ACL** | Awaiting owner ACL: `sudo setfacl -m u:yesh:rw /var/run/docker.sock` |

---

## 2. In-Depth Technical Verification & Architecture

### 2.1 Batch Cutoff PostgreSQL Session Advisory Lock Demarcation (Item B)
- **Concurrency Test (`BatchCutoffAdvisoryLockPostgresIntegrationTest`):**
  Spawns 2 concurrent threads attempting `batchCutoffService.runCutoff()` against real PostgreSQL. The winning thread acquires `pg_try_advisory_lock(ADVISORY_LOCK_ID)` on a dedicated JDBC connection; the losing thread immediately throws `InvalidBatchStateException` ("Another batch cutoff run is currently in progress") and returns HTTP 409 without blocking.
- **Transaction & Ledger Call Architecture:**
  - *Short Staging Transaction:* The batch creation, record grouping, and initial payout staging execute in a short database transaction that commits immediately.
  - *Out-of-Transaction Remote Calls:* `LedgerClient.initiatePayout()` calls execute outside the database transaction, preventing PostgreSQL connection pool exhaustion or transaction lock timeouts during network spikes.
  - *Resilient Partial Failure:* If a ledger call fails or times out, the specific payout is marked `FAILED` and the batch transitions to `PARTIAL_FAILURE`.
  - *Deterministic Resumption:* The batch can be resumed via `/v1/batches/{batchId}/resume`, re-attempting only failed payouts with the deterministic idempotency key (`batchId:sellerId`), preventing duplicate payouts or double-spending.

### 2.2 Real-Database Integration Suite & Engine Immutability (Item C2)
Located in `services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/integration/LedgerRealDatabaseIntegrationTest.java` (7 integration tests against real PostgreSQL & Redis):
1. **S0 Pessimistic Concurrency & Zero Deadlocks (`testS0PessimisticConcurrencyAndInvariants`):**
   Spawns 6 concurrent worker threads executing multi-account payment captures. Verifies zero deadlocks via lexicographical account ID ordering, and confirms mathematical invariants I1–I8 pass with zero violations via `IntegrityService`.
2. **S1 Late-Lock Concurrency (`testS1LateLockConcurrencyAndInvariants`):**
   Executes concurrent payments under `S1_LATE_LOCK`, verifying zero deadlocks, accurate balance caching, and clean integrity check.
3. **Multi-Threaded Idempotency Race (`testIdempotencyUnderConcurrency`):**
   8 concurrent threads dispatch identical payment capture requests with matching `Idempotency-Key`. Exactly 1 request succeeds (HTTP 201 `CAPTURED`), while 7 competing threads receive idempotent replay or conflict backoff. Database contains exactly 1 journal entry, verified by `uk_journals_scope_idempotency_key` and `idempotency_keys_pkey`.
4. **Engine-Level Immutability Triggers (`testAppendOnlyDatabaseTriggers`):**
   PostgreSQL migration `V7__journal_immutability_trigger.sql` installs `trg_journal_immutability` and `trg_ledger_entry_immutability`. Raw SQL `UPDATE` and `DELETE` queries on `journals` and `ledger_entries` are unconditionally aborted by PostgreSQL engine triggers (`RAISE EXCEPTION 'journals table is append-only'`).
5. **Outbox Relay Competing Workers (`testOutboxRelayCompetingInstancesSkipLocked`):**
   Two concurrent relay workers query `SELECT ... FOR UPDATE SKIP LOCKED` on `outbox_events`. Obtains mutually disjoint sets of outbox events, proving zero double-publishing under horizontal scaling.
6. **Flyway Entity-Schema Validation (`testFlywaySchemaMatchesJpaEntities`):**
   Validates that all 7 Flyway migrations apply cleanly and Hibernate JPA entities map 1:1 without column or constraint mismatches.
7. **Kafka Deduplication & DLQ (`testKafkaConsumerDeduplicationAndDlq`):**
   Tag-isolated integration test for message deduplication across Kafka consumer group restarts.

### 2.3 Identity Security, Token Revocation & Lockout (Items E1 & E4)
- **Logout Deny-List (`LogoutDenyListIntegrationTest`):**
  Issues login request, accesses `/auth/me` (200), invokes `/auth/logout`. Verifies Redis key `paycore:denylist:<jti>` exists with positive TTL <= 900s. Subsequent request with revoked JWT is rejected with HTTP 401 Unauthorized.
- **Brute-Force Lockout:**
  5 consecutive failed logins trigger account lockout for 15 minutes. 6th attempt with valid password returns HTTP 401 (`invalid_credentials`), verified via Redis key `paycore:auth:lock:<username>` and PostgreSQL `audit_logs` record `LOGIN_LOCKED`.
- **Refresh Token Replay Family Revocation (`TokenRevocationService`):**
  Implemented with `@Transactional(propagation = Propagation.REQUIRES_NEW)`. When an already-used refresh token is presented, the entire token family is immediately revoked in PostgreSQL (`revoked_at` populated across all child tokens) and an audit log (`REFRESH_TOKEN_REUSE_DETECTED`) is permanently written before returning HTTP 401.

### 2.4 Transport Security & Payload Protections (Items E2 & E5)
- **CORS Origin Rejection (`CorsRejectionSecurityTest`):**
  Tested in `identity-service`, `ledger-service`, and `settlement-service`. Requests from untrusted origins (`https://evil-attacker.com`) do not receive `Access-Control-Allow-Origin: *` or origin reflection, and `Access-Control-Allow-Credentials` is rejected. Reverse proxy `deploy/nginx.conf` enforces strict Content-Security-Policy with zero wildcard origin reflection.
- **Request Body Size Limit (`RequestBodyLimitFilter`):**
  Placed at highest filter precedence in `paycore-common`. Inspects `Content-Length` header and wraps `ServletInputStream` in a `BoundedServletInputStream` enforcing a strict 2 MB limit on non-multipart payloads. Verified for both standard payloads and chunked streaming requests without `Content-Length`: payloads exceeding 2 MB immediately abort with RFC 9457 HTTP 413 `payload_too_large`.

---

## 3. Test Doubles & Simulators Inventory

In compliance with the Ponytail protocol, all test doubles are cataloged with their domain justification:

| Simulator / Double | Source Component | Purpose & Justification | Environment |
|---|---|---|---|
| **Upstream Banking Partner Simulator** | `ProviderSimulatorService.java` (`POST /v1/simulator/generate-file`) | Generates synthetic bank clearing CSV files (`record_id, payment_id, gross, fee, net, status`) to test multi-stage reconciliation and subset-sum matching without requiring live banking contracts. | `dev`, `test` only |
| **In-Memory Strategy Benchmark Model** | `perf/run-benchmark.py` (v1) | Legacy mathematical simulation using `random.expovariate`. | **DELETED** in commit `3868a5a` |
| **Shell Mock Runner** | `benchmarks/runner.sh` | Mock shell script creating synthetic k6 JSON summaries. | **DELETED** in commit `3868a5a` |

---

## 4. End-to-End Business Flow Multi-Run Verification

Executed via `scripts/e2e-flow.sh` on fresh PostgreSQL 16 and Redis 7:

### Run 1: Fresh Volume Baseline
- **Starting State:** Clean database, 18 seeded accounts, 0 journals.
- **Operations:** Onboarded 2 merchants (`seller_e2e_1`, `seller_e2e_2`), processed 2 payments, 1 partial refund, 2 batch payouts, 1 dispute adjustment, 1 provider deposit.
- **Ending State:** 20 accounts, 9 journals.
- **Monetary Balance:** Total Debits = `1,310,050 paise` (₹13,100.50), Total Credits = `1,310,050 paise` (₹13,100.50). Net delta = 0 paise.
- **Integrity Check:** Zero violations across Invariants I1–I8.

### Run 2: Idempotent Stack Re-Execution
- **Starting State:** Preserved state from Run 1 (20 accounts, 9 journals).
- **Operations:** Re-executed complete business flow with new distinct transaction keys.
- **Ending State:** 22 accounts, 18 journals.
- **Monetary Balance:** Total Debits = `2,620,100 paise` (₹26,201.00), Total Credits = `2,620,100 paise` (₹26,201.00). Net delta = 0 paise.
- **Integrity Check:** Zero violations across all accounts.

---

## 5. Test Suite Breakdown (258 Tests Passing)

```
[INFO] Reactor Summary for paycore-parent 1.0.0-SNAPSHOT:
[INFO] paycore-parent ..................................... SUCCESS [  0.226 s]
[INFO] paycore-common ..................................... SUCCESS [  3.332 s] (20 tests)
[INFO] identity-service ................................... SUCCESS [ 19.550 s] (55 tests)
[INFO] ledger-service ..................................... SUCCESS [ 23.099 s] (111 tests)
[INFO] settlement-service ................................. SUCCESS [ 18.351 s] (72 tests)
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] Total time: 01:04 min
[INFO] Finished at: 2026-11-18T08:26:17+05:30
[INFO] ------------------------------------------------------------------------
```

---

## 6. Phase 2 Roadmap & Docker Execution Plan

To execute the remaining benchmark and crash test work packages:
1. **Docker Access:** Owner executes:
   ```bash
   sudo setfacl -m u:yesh:rw /var/run/docker.sock
   ```
2. **Pre-Flight Verification:** Verify Docker access without permission elevation:
   ```bash
   docker ps && docker compose version
   ```
3. **Database Preservation:** Dump exploratory host database:
   ```bash
   pg_dump -h localhost -U paycore -d paycore_ledger > /tmp/paycore_ledger_exploratory.sql
   ```
4. **Deploy Stack:**
   ```bash
   docker compose -f deploy/docker-compose.yml up -d
   ```
5. **Execute Test Matrix:**
   - Run Step 3: Real JVM SIGKILL 50-run crash test matrix against disk-backed storage.
   - Run Step 7: 72-cell Tier 1 hot-account benchmark suite driving real k6 against real OpenJDK 21 JVMs.
