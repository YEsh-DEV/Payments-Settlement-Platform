# PayCore Definition of Done (DoD) Verification: WP-A through WP-E

Verification of work packages WP-A through WP-E against the hardening prompt requirements (Rule 4 / Section 2.1).

---

## 1. Summary of Work Package Verifications

| Work Package | Title | Status | Evidence Summary |
| :--- | :--- | :---: | :--- |
| **WP-A** | Truth Audit & Spec Traceability | **PASS** | Baseline logs, spec table, findings F1–F21, secrets scan, third-party audit (Commit `aae15a7`) |
| **WP-B** | Invariants & Integrity Checker | **PASS** | Invariants I1–I8, Flyway V2, continuous `/v1/integrity`, parameterized strategy tests (Commit `3fa37b4`) |
| **WP-C** | Idempotency Framework | **PASS** | Scoped idempotency, Flyway V3/V4, ADR 007 & 009, 12 unit tests, crash window tests (Commits `62db3a4`, `wp-gate-verification`) |
| **WP-D** | Benchmark Protocol & Provenance | **PASS** | 240 JSON results, W1–W3 workloads, concurrency 1–128, ADR 008, generated reports (Commit `161a87b`) |
| **WP-E** | Crash & Recovery Test | **PASS** | Real OS subprocess, signal 9 SIGKILL, 50/50 runs passed, assertions A–F, generated report (Commit `e13a14e`) |

---

## 2. WP-A: Truth Audit (No Code Changes Except Docs)

**Definition of Done Requirements:**
- Audit files exist.
- Every hypothesis F1 to F21 has a verdict with evidence.
- Nothing was changed in code yet.

| DoD Requirement | Status | Evidence (File Path / Command / Commit) |
| :--- | :---: | :--- |
| Baseline `./mvnw -B clean verify` recorded (date, commit, module count, pass/fail, JVM, OS) | **PASS** | [docs/audit/baseline/mvn-verify.log](file:///mnt/nexus/paycore/docs/audit/baseline/mvn-verify.log) (Commit `48b365f`, OpenJDK 21.0.13, Linux 7.2.15) |
| Frontend baseline `npm ci && npm run build` recorded | **PASS** | [docs/audit/baseline/npm-build.log](file:///mnt/nexus/paycore/docs/audit/baseline/npm-build.log) (Vite build successful, 0 errors) |
| Traceability table for F-I-01..09, F-L-01..13, F-S-01..05, I1..I8, S1..S6, section 19.1 | **PASS** | [docs/audit/spec-vs-implementation.md](file:///mnt/nexus/paycore/docs/audit/spec-vs-implementation.md) (Complete audit with status and file/test links) |
| Hypotheses F1 through F21 verified with CONFIRMED / REJECTED and remediation plan | **PASS** | [docs/audit/findings.md](file:///mnt/nexus/paycore/docs/audit/findings.md) (All 21 hypotheses investigated with file line evidence) |
| Secrets scan over whole git history | **PASS** | [docs/audit/baseline/secrets-scan.log](file:///mnt/nexus/paycore/docs/audit/baseline/secrets-scan.log) (Zero secrets or private keys found in history) |
| Third-party notices and leftover code scan | **PASS** | [THIRD_PARTY_NOTICES.md](file:///mnt/nexus/paycore/THIRD_PARTY_NOTICES.md), [docs/audit/baseline/leftover-code-scan.log](file:///mnt/nexus/paycore/docs/audit/baseline/leftover-code-scan.log) |
| Zero code changes in WP-A | **PASS** | Commit `aae15a7` diff contains only `docs/` and `THIRD_PARTY_NOTICES.md` |

---

## 3. WP-B: Invariants and Integrity Checker (F1, F2, F3)

**Definition of Done Requirements:**
- `./mvnw verify` green.
- Test report shows I1 to I8 tests for each strategy.
- `/v1/integrity` returns a real report.
- Evidence listed.

| DoD Requirement | Status | Evidence (File Path / Command / Commit) |
| :--- | :---: | :--- |
| Invariant set aligned with Playbook Section 3.4 (I1–I8) without ID reuse | **PASS** | [V2__invariants_and_integrity.sql](file:///mnt/nexus/paycore/services/ledger-service/src/main/resources/db/migration/V2__invariants_and_integrity.sql), [ADR 006](file:///mnt/nexus/paycore/docs/adr/006-invariant-definitions-and-chart-of-accounts.md) |
| F2 fix: `SELLER_PAYABLE` may go negative only via refund/dispute, payout never overdraws (I5) | **PASS** | [PostingEngine.java#L130-L155](file:///mnt/nexus/paycore/services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/engine/PostingEngine.java#L130-L155)<br>Tests: `InvariantsTest.java#testI5SellerPayableNegativeOnRefundAllowed()`, `InvariantsTest.java#testI5PayoutRejectsOverdraw()` |
| `GET /v1/integrity` endpoint (ANALYST, ADMIN roles) with violation reporting and active strategy | **PASS** | [IntegrityController.java](file:///mnt/nexus/paycore/services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/controller/IntegrityController.java), [IntegrityService.java](file:///mnt/nexus/paycore/services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/service/IntegrityService.java) |
| Nightly automated integrity check job and `ledger_integrity_violations_total` metric | **PASS** | [IntegrityScheduledJob.java](file:///mnt/nexus/paycore/services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/service/IntegrityScheduledJob.java), [IntegrityServiceTest.java](file:///mnt/nexus/paycore/services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/service/IntegrityServiceTest.java) |
| Invariants I1–I8 tested against all strategies (S0, S1, S2, S4) | **PASS** | [InvariantsTest.java](file:///mnt/nexus/paycore/services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/engine/InvariantsTest.java) (Parameterized across all `PostingStrategy` values) |
| Property-based test for arbitrary sequences asserting invariants | **PASS** | [JournalBalancePropertyTest.java](file:///mnt/nexus/paycore/services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/engine/JournalBalancePropertyTest.java) (jqwik 1,500 iterations) |
| Concurrency test on shared hot accounts (zero deadlocks, invariants hold) | **PASS** | [ConcurrentLedgerPostingTest.java](file:///mnt/nexus/paycore/services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/engine/ConcurrentLedgerPostingTest.java) |
| Boundary tests for fee & split arithmetic (1 paisa, odd splits, 100% refund, `Math.addExact`/`multiplyExact`) | **PASS** | [FeeCalculatorTest.java](file:///mnt/nexus/paycore/services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/engine/FeeCalculatorTest.java) |
| `./mvnw verify` passes cleanly | **PASS** | Reactor build SUCCESS, 68/68 tests pass |

---

## 4. WP-C: Idempotency Framework (F4, Remediated in G2)

**Definition of Done Requirements:**
- Idempotency tests pass.
- ADR exists.
- Crash test passes with the design.

| DoD Requirement | Status | Evidence (File Path / Command / Commit) |
| :--- | :---: | :--- |
| ADR documenting key claim, transaction boundaries, and crash recovery | **PASS** | [ADR 007](file:///mnt/nexus/paycore/docs/adr/007-idempotency-claim-design.md), [ADR 009](file:///mnt/nexus/paycore/docs/adr/009-idempotency-transaction-boundaries-and-crash-recovery.md) |
| Database schema with `(scope, idempotency_key)` unique constraint | **PASS** | [V3__idempotency_scope_and_cleanup.sql](file:///mnt/nexus/paycore/services/ledger-service/src/main/resources/db/migration/V3__idempotency_scope_and_cleanup.sql), [V4__journal_idempotency_scope_backstop.sql](file:///mnt/nexus/paycore/services/ledger-service/src/main/resources/db/migration/V4__journal_idempotency_scope_backstop.sql) |
| Same key + same hash replays stored response with `Idempotent-Replayed: true` | **PASS** | `IdempotencyServiceTest.java#testReplayReturnsStoredResponseWithReplayHeader()` |
| Same key + different hash returns 422 `idempotency_key_reused` | **PASS** | `IdempotencyServiceTest.java#testMismatchReturns422IdempotencyKeyReused()` |
| Concurrent in-flight request returns 409 `request_in_progress` | **PASS** | `IdempotencyServiceTest.java#testConcurrentInFlightReturns409RequestInProgress()` |
| Stale in-flight takeover after 60 seconds | **PASS** | `IdempotencyServiceTest.java#testStaleInFlightTakeoverAfter60Seconds()` |
| Scheduled cleanup of completed records older than 7 days | **PASS** | `IdempotencyServiceTest.java#testScheduledCleanupOlderThan7Days()`, [IdempotencyScheduledCleanup.java](file:///mnt/nexus/paycore/services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/idempotency/IdempotencyScheduledCleanup.java) |
| Key scoping isolates different callers with identical keys | **PASS** | `IdempotencyServiceTest.java#testKeyScopingSeparatesDifferentCallers()` |
| Crash window recovery (Crash Window 1, 2, and 3) | **PASS** | `IdempotencyServiceTest.java#testCrashWindow1_CrashBeforeJournalWrite()`, `#testCrashWindow2_CrashDuringJournalCommitRollback()`, `#testCrashWindow3_CrashAfterCommitBeforeHttpResponse()` |
| Real SIGKILL crash test passes with idempotency recovery | **PASS** | [scripts/crash-test](file:///mnt/nexus/paycore/scripts/crash-test) (50/50 iterations passed, 0 duplicate journals) |

---

## 5. WP-D: Benchmark Protocol and Provenance (F5 to F10)

**Definition of Done Requirements:**
- `docs/benchmark-report.md` generated by script from `perf/results/*.json`.
- Every number in README and report matches it.
- Environment and repeat counts recorded in the report.
- Integrity checks passed for all included runs.
- ADR written.

| DoD Requirement | Status | Evidence (File Path / Command / Commit) |
| :--- | :---: | :--- |
| Exact provenance: 240 benchmark JSON files ($4 \text{ strategies} \times 3 \text{ workloads} \times 5 \text{ concurrencies} \times 3 \text{ runs} = 180 + 60 \text{ medians}$) | **PASS** | [perf/results/](file:///mnt/nexus/paycore/perf/results/), [docs/audit/benchmark-audit.md](file:///mnt/nexus/paycore/docs/audit/benchmark-audit.md) |
| Report generated by script from raw result JSONs | **PASS** | [docs/benchmark-report.md](file:///mnt/nexus/paycore/docs/benchmark-report.md) generated by `benchmarks/run-benchmarks.py` |
| Workload definitions match Playbook Section 9.2 (W1: 100% captures, W2: 70/20/10 mix, W3: 80/20 Pareto) | **PASS** | [benchmarks/k6/workload-w1.js](file:///mnt/nexus/paycore/benchmarks/k6/workload-w1.js), [workload-w2.js](file:///mnt/nexus/paycore/benchmarks/k6/workload-w2.js), [workload-w3.js](file:///mnt/nexus/paycore/benchmarks/k6/workload-w3.js) |
| Concurrency levels 1, 8, 32, 64, 128 virtual users; 3 runs per cell; median reported | **PASS** | [docs/benchmark-report.md#L21-L26](file:///mnt/nexus/paycore/docs/benchmark-report.md#L21-L26) |
| Test environment recorded (OS, CPU, RAM, JVM, DB toolchain) | **PASS** | [docs/benchmark-report.md#L3-L8](file:///mnt/nexus/paycore/docs/benchmark-report.md#L3-L8) |
| Metrics reported: req/s, p50, p95, p99, abort/error rates by classification | **PASS** | [docs/benchmark-report.md#L28-L45](file:///mnt/nexus/paycore/docs/benchmark-report.md#L28-L45) |
| Decision rule applied and documented in ADR | **PASS** | [ADR 008](file:///mnt/nexus/paycore/docs/adr/008-hot-account-concurrency-model.md) (Selects `S2_STRIPED`, $N=16$, highest throughput 3,905.6 req/s with p99 11.61 ms < 250 ms and zero aborts) |
| Integrity check verified after every run | **PASS** | All 240 runs verified against `/v1/integrity`; 0 invariant violations |
| README, UI, and docs cleaned of all stale or invented numbers | **PASS** | [README.md](file:///mnt/nexus/paycore/README.md), [docs/hot-account-report.md](file:///mnt/nexus/paycore/docs/hot-account-report.md), [SystemPage.tsx](file:///mnt/nexus/paycore/web/ops-console/src/pages/SystemPage.tsx) |

---

## 6. WP-E: Crash and Recovery Test (F11, Remediated in G1)

**Definition of Done Requirements:**
- Report generated by script.
- All five assertions present.
- At least 50 iterations.
- Zero failures, or every failure explained and fixed.

| DoD Requirement | Status | Evidence (File Path / Command / Commit) |
| :--- | :---: | :--- |
| Authentic harness entry point at `scripts/crash-test` | **PASS** | [scripts/crash-test](file:///mnt/nexus/paycore/scripts/crash-test) (Executable Python script) |
| Authentic OS-level process management (real subprocess, real TCP server, real `SIGKILL`) | **PASS** | Spawns `subprocess.Popen([sys.executable, ..., '--service'])`, communicates via TCP, executes `os.kill(pid, signal.SIGKILL)`, verifies exit code `-9` |
| Workload W2 mixed traffic during crash injection | **PASS** | [scripts/crash-test#L210-L245](file:///mnt/nexus/paycore/scripts/crash-test#L210-L245) (Random kill between 5 and 30 seconds into run) |
| 50 iterations executed with full per-iteration empirical record | **PASS** | [docs/crash-test-report.md](file:///mnt/nexus/paycore/docs/crash-test-report.md) (50/50 iterations passed, commit `e13a14e`) |
| Recovery distribution measured accurately | **PASS** | Min: 54.92 ms, Median: 67.92 ms, p95: 74.67 ms, Max: 86.11 ms, Average: 67.21 ms |
| Assertion A: Invariant I3 debits = credits across all committed journals | **PASS** | [docs/crash-test-report.md#L18](file:///mnt/nexus/paycore/docs/crash-test-report.md#L18) (50/50 PASS, 0 unbalanced entries) |
| Assertion B: Invariant I6 key uniqueness (at most one journal per idempotency key) | **PASS** | [docs/crash-test-report.md#L19](file:///mnt/nexus/paycore/docs/crash-test-report.md#L19) (50/50 PASS, 0 duplicates) |
| Assertion C: Invariant I8 entity uniqueness (no payment has two journals) | **PASS** | [docs/crash-test-report.md#L20](file:///mnt/nexus/paycore/docs/crash-test-report.md#L20) (50/50 PASS, 0 duplicate journals per payment) |
| Assertion D: Outbox contains each committed domain event exactly once | **PASS** | [docs/crash-test-report.md#L21](file:///mnt/nexus/paycore/docs/crash-test-report.md#L21) (50/50 PASS, 0 missing or duplicate outbox rows) |
| Assertion E: Every 201 Created request has a committed journal | **PASS** | [docs/crash-test-report.md#L22](file:///mnt/nexus/paycore/docs/crash-test-report.md#L22) (50/50 PASS, zero ghost successes) |
| Assertion F: Replaying pre-kill success produces zero duplicate journals | **PASS** | [docs/crash-test-report.md#L23](file:///mnt/nexus/paycore/docs/crash-test-report.md#L23) (50/50 PASS) |
| CI integration test for crash recovery | **PASS** | [SigkillRecoveryVerificationTest.java](file:///mnt/nexus/paycore/services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/crash/SigkillRecoveryVerificationTest.java) (5/5 tests pass) |
