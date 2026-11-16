# PayCore Definition of Done (DoD) Verification: WP-A through WP-E

**Document Version:** 2.0.0 (Hardened following WP-GATE-2 Audit)  
**Audit Context:** Complete Traceability and Evidence Verification  
**Branch:** `wp-gate-2`  
**All Evidence Captured in:** `docs/audit/evidence/`  

---

## 1. Summary of Work Package Verifications

| Work Package | Title | Initial Status | WP-GATE-2 Status | Evidence Summary |
| :--- | :--- | :---: | :---: | :--- |
| **WP-A** | Truth Audit & Spec Traceability | PASS | **PASS** | Baseline logs, spec table, findings F1–F21, secrets scan, third-party audit ([docs/audit/findings.md](file:///mnt/nexus/paycore/docs/audit/findings.md)) |
| **WP-B** | Invariants & Integrity Checker | PASS | **PASS** | Invariants I1–I8, Flyway V2, continuous `/v1/integrity`, parameterized strategy tests ([V2__invariants_and_integrity.sql](file:///mnt/nexus/paycore/services/ledger-service/src/main/resources/db/migration/V2__invariants_and_integrity.sql)) |
| **WP-C** | Idempotency Framework | INCOMPLETE | **FIXED** | Atomic transaction boundary joining, Flyway V5 status code, scoped isolation, live PostgreSQL 16 test suite ([IdempotencyPostgresTransactionTest.java](file:///mnt/nexus/paycore/services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/idempotency/IdempotencyPostgresTransactionTest.java)) |
| **WP-D** | Benchmark Protocol & Provenance | FAKE | **RETRACTED** | Mathematical simulation discovered in `perf/run-benchmark.py`. 240 synthetic JSON files deleted. Synthetic numbers excised from README/docs. Real k6 runner in `perf/k6/`. Rerun pending owner duration choice ([docs/audit/benchmark-provenance.md](file:///mnt/nexus/paycore/docs/audit/benchmark-provenance.md)) |
| **WP-E** | Crash & Recovery Test | FAKE | **FIXED** | Replaced Python mock server (`sys.executable --service`) with real OpenJDK 21 JVM (`ledger-service-1.0.0-SNAPSHOT.jar`) + PostgreSQL 16 SIGKILL harness (`scripts/crash-test`). Real recovery ~6.1–8.9s ([docs/audit/evidence/crash-test-process.txt](file:///mnt/nexus/paycore/docs/audit/evidence/crash-test-process.txt)) |

---

## 2. WP-A: Truth Audit (No Code Changes Except Docs)

| DoD Requirement | Status | Evidence (File Path / Command / Commit) |
| :--- | :---: | :--- |
| Baseline `./mvnw -B clean verify` recorded | **PASS** | [docs/audit/baseline/mvn-verify.log](file:///mnt/nexus/paycore/docs/audit/baseline/mvn-verify.log) |
| Frontend baseline `npm ci && npm run build` recorded | **PASS** | [docs/audit/baseline/npm-build.log](file:///mnt/nexus/paycore/docs/audit/baseline/npm-build.log) |
| Traceability table for F-I, F-L, F-S, I1..I8, S1..S6 | **PASS** | [docs/audit/spec-vs-implementation.md](file:///mnt/nexus/paycore/docs/audit/spec-vs-implementation.md) |
| Hypotheses F1 through F21 verified with CONFIRMED / REJECTED | **PASS** | [docs/audit/findings.md](file:///mnt/nexus/paycore/docs/audit/findings.md) |
| Secrets scan over whole git history | **PASS** | [docs/audit/baseline/secrets-scan.log](file:///mnt/nexus/paycore/docs/audit/baseline/secrets-scan.log) |
| Third-party notices and leftover code scan | **PASS** | [THIRD_PARTY_NOTICES.md](file:///mnt/nexus/paycore/THIRD_PARTY_NOTICES.md) |

---

## 3. WP-B: Invariants and Integrity Checker (F1, F2, F3)

| DoD Requirement | Status | Evidence (File Path / Command / Commit) |
| :--- | :---: | :--- |
| Invariant set aligned with Playbook Section 3.4 (I1–I8) without ID reuse | **PASS** | [V2__invariants_and_integrity.sql](file:///mnt/nexus/paycore/services/ledger-service/src/main/resources/db/migration/V2__invariants_and_integrity.sql) |
| F2 fix: `SELLER_PAYABLE` may go negative only via refund/dispute | **PASS** | [PostingEngine.java](file:///mnt/nexus/paycore/services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/engine/PostingEngine.java) |
| `GET /v1/integrity` endpoint with violation reporting and active strategy | **PASS** | [IntegrityController.java](file:///mnt/nexus/paycore/services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/controller/IntegrityController.java) |
| Invariants I1–I8 tested against all strategies (S0, S1, S2, S4) | **PASS** | [InvariantsTest.java](file:///mnt/nexus/paycore/services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/engine/InvariantsTest.java) |
| Property-based test for arbitrary sequences asserting invariants | **PASS** | [JournalBalancePropertyTest.java](file:///mnt/nexus/paycore/services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/engine/JournalBalancePropertyTest.java) |

---

## 4. WP-C: Idempotency Framework (Remediated in WP-GATE-2C)

| DoD Requirement | Status | Evidence (File Path / Command / Commit) |
| :--- | :---: | :--- |
| ADR documenting key claim, transaction boundaries, and crash recovery | **PASS** | [ADR 009](file:///mnt/nexus/paycore/docs/adr/009-idempotency-transaction-boundaries-and-crash-recovery.md) |
| Database schema with `(scope, idempotency_key)` compound unique constraint | **PASS** | [V4__journal_idempotency_scope_backstop.sql](file:///mnt/nexus/paycore/services/ledger-service/src/main/resources/db/migration/V4__journal_idempotency_scope_backstop.sql) |
| HTTP status code preserved and replayed on idempotent hits | **FIXED** | [V5__idempotency_status_code.sql](file:///mnt/nexus/paycore/services/ledger-service/src/main/resources/db/migration/V5__idempotency_status_code.sql), [IdempotencyRecord.java](file:///mnt/nexus/paycore/services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/domain/IdempotencyRecord.java) |
| Atomic transaction joining (status transition in same TX as journal/outbox) | **FIXED** | [PaymentService.java](file:///mnt/nexus/paycore/services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/service/PaymentService.java), [RefundService.java](file:///mnt/nexus/paycore/services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/service/RefundService.java), [PayoutService.java](file:///mnt/nexus/paycore/services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/service/PayoutService.java) |
| Spring AOP self-invocation bypass resolved | **FIXED** | Self-injection via `@Lazy` proxy in [IdempotencyService.java](file:///mnt/nexus/paycore/services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/idempotency/IdempotencyService.java) |
| Integration tests against live PostgreSQL 16 (rollback & commit parity) | **PASS** | [IdempotencyPostgresTransactionTest.java](file:///mnt/nexus/paycore/services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/idempotency/IdempotencyPostgresTransactionTest.java) |

---

## 5. WP-D: Benchmark Protocol and Provenance (Audited in WP-GATE-2B)

| DoD Requirement | Status | Evidence (File Path / Command / Commit) |
| :--- | :---: | :--- |
| Exact provenance: Benchmark driven against live Spring Boot and PostgreSQL | **FAILED (RETRACTED)** | Audit in [docs/audit/benchmark-provenance.md](file:///mnt/nexus/paycore/docs/audit/benchmark-provenance.md) confirmed Python script used `random.expovariate` formula. All 240 synthetic JSONs purged. |
| README, UI, and docs cleaned of all synthetic/fabricated numbers | **FIXED** | Grep scan evidence in [docs/audit/evidence/repo-scan-numbers.txt](file:///mnt/nexus/paycore/docs/audit/evidence/repo-scan-numbers.txt) confirms zero stale/synthetic numbers across `README.md`, `web/ops-console`, and `services/`. |
| Real k6 workload scripts structured | **PASS** | Real k6 scripts organized in [perf/k6/](file:///mnt/nexus/paycore/perf/k6/) (`workload-w1.js`, `workload-w2.js`, `workload-w3.js`). |
| Rerun protocol documented with runtime estimates | **DOCUMENTED** | Full 180-run matrix (~7.5 hours) vs focused 12-run matrix (~20 mins) documented in [docs/benchmark-report.md](file:///mnt/nexus/paycore/docs/benchmark-report.md). Awaiting owner decision. |

---

## 6. WP-E: Crash and Recovery Test (Remediated in WP-GATE-2A)

| DoD Requirement | Status | Evidence (File Path / Command / Commit) |
| :--- | :---: | :--- |
| Real OS-level process management (OpenJDK 21 JVM + PostgreSQL 16) | **FIXED** | [scripts/crash-test](file:///mnt/nexus/paycore/scripts/crash-test) executes `java -jar services/ledger-service/target/ledger-service-1.0.0-SNAPSHOT.jar`. Zero Python mock servers. |
| Process provenance captured from OS `/proc/<pid>/cmdline` | **PASS** | Captured in [docs/audit/evidence/crash-test-process.txt](file:///mnt/nexus/paycore/docs/audit/evidence/crash-test-process.txt). |
| Direct kernel `SIGKILL` (`kill -9 <pid> / signal.SIGKILL`) | **PASS** | Harness delivers signal 9 and validates return code `-9`. |
| Workload W2 mixed traffic during crash injection | **PASS** | Concurrent captures, refunds, and payouts dispatched over HTTP during crash. |
| 50 iterations JVM SIGKILL + 20 iterations PostgreSQL WAL SIGKILL | **PASS** | Full empirical results table in [docs/crash-test-report.md](file:///mnt/nexus/paycore/docs/crash-test-report.md). |
| Real JVM recovery distribution measured | **FIXED** | Real recovery takes ~6.1 to 8.9 seconds (replaces artificial 67 ms in-memory pause). |
| Assertions A through F verified directly in PostgreSQL | **PASS** | 100% pass rate: 0 unbalanced entries (I3), 0 duplicate keys (I6), 0 duplicate journals per payment (I8), complete outbox, zero double-spend on replay. |
| Unit test renamed to clarify scope | **FIXED** | Renamed `SigkillRecoveryVerificationTest.java` to [IdempotencyReplayAfterRestartTest.java](file:///mnt/nexus/paycore/services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/crash/IdempotencyReplayAfterRestartTest.java). |
