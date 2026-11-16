# PayCore Fakes, Simulators, and Test Doubles Catalog

**Document Version:** 1.0.0  
**Audit Context:** WP-GATE-2D Provenance and Integrity Verification  
**Repository:** `YEsh-DEV/Payments-Settlement-Platform`  
**Branch:** `wp-gate-2`  

---

## 1. Scope and Objective

This catalog provides an exhaustive, transparent inventory of every simulator, mock, fake, and synthetic generator present in the PayCore repository. Following the WP-GATE-2 audit findings, PayCore enforces a strict transparency rule: **never hide a simulation, never claim empirical behavior for synthetic models, and explicitly label every test double with its architectural justification.**

---

## 2. Inventory of Test Doubles and Simulators

### 2.1 Upstream Banking Partner Simulator (`ProviderSimulatorService`)

| Property | Details |
| :--- | :--- |
| **Component** | `io.github.yesh.paycore.settlement.service.ProviderSimulatorService` |
| **Controller** | `io.github.yesh.paycore.settlement.controller.SimulatorController` (`POST /v1/simulator/generate-file`) |
| **Type** | Synthetic Domain Partner Simulator |
| **Artifacts Generated** | Bank clearing settlement files (`.csv`, formatted with standard settlement headers: `record_id, payment_id, gross_amount_paise, fee_amount_paise, net_amount_paise, status, settled_at`) |
| **Purpose & Justification** | Simulates external banking networks and acquiring partners (e.g., Stripe, Chase, Adyen, NPCI) for multi-stage settlement reconciliation (Playbook Section 5 & 6). External bank rails cannot be integrated directly into local developer environments without live banking credentials, clearing accounts, and SFTP endpoints. The simulator allows deterministic injection of reconciliation edge cases: missing clearing records, unexpected transactions, fee discrepancies, and duplicate batch settlements. |
| **Production Exposure** | Restricted to `dev` and `test` environments. Disabled or unmapped under production security profiles. |

---

### 2.2 Retracted Benchmark In-Memory Model (`perf/run-benchmark.py`)

| Property | Details |
| :--- | :--- |
| **Component** | `perf/run-benchmark.py` (`simulate_strategy_latency`) |
| **Type** | Mathematical Model / Synthetic Simulation (RETRACTED) |
| **Finding** | The benchmark runner executed a mathematical latency equation (`random.expovariate` and queue delay multipliers) in Python rather than dispatching HTTP traffic against the live Spring Boot ledger JVM and PostgreSQL database. |
| **Remediation & Status** | **RETRACTED AND PURGED.** All 240 synthetic JSON result files in `perf/results/` were deleted. All claims citing 3,906 TPS or synthetic queue latencies were excised from `README.md`, `docs/benchmark-report.md`, and `web/ops-console`. Full audit trail documented in [`docs/audit/benchmark-provenance.md`](file:///mnt/nexus/paycore/docs/audit/benchmark-provenance.md). |
| **Next Step** | Real k6 benchmark driver created in `perf/k6/` to drive real HTTP load against live Spring Boot and PostgreSQL. Benchmark rerun scheduled following owner review. |

---

### 2.3 Unit Test Repository Mocks (Mockito Test Doubles)

| Property | Details |
| :--- | :--- |
| **Components** | `IdempotencyReplayAfterRestartTest.java`, `PaymentServiceTest.java`, `RefundServiceTest.java`, `PayoutServiceTest.java` |
| **Type** | In-Memory Mockito Stubs (`@Mock`, `when(...).thenReturn(...)`) |
| **Purpose & Justification** | Standard unit-level isolation to verify algorithm branching, error handling, input validation, and transactional annotations without spinning up database containers. |
| **Honesty Boundary** | These tests are strictly categorized as **unit tests**. Previously, `SigkillRecoveryVerificationTest` incorrectly suggested that throwing an in-process `RuntimeException` represented a real OS process crash. This class was renamed to `IdempotencyReplayAfterRestartTest`, its comments corrected to state that it tests rollback and replay branch logic, and real process crash testing was moved entirely to `scripts/crash-test`. |

---

## 3. Real Process Environments (Confirmed Non-Simulations)

To contrast with the test doubles above, the following components are verified to execute against **real operating system processes and persistent storage**:

1. **Crash & Recovery Harness (`scripts/crash-test`):**
   - **Target:** Real OpenJDK 21 JVM process (`java -jar services/ledger-service/target/ledger-service-1.0.0-SNAPSHOT.jar`).
   - **Database:** Live native PostgreSQL 16.6 on port 5432 with persistent disk tables and WAL.
   - **Cache:** Live Redis 8.10 on port 6379.
   - **Termination:** Direct host kernel `SIGKILL` (signal 9 / `kill -9 <pid>`).
   - **Metrics:** Real wall-clock recovery times (~8.1 to 8.7 seconds for JVM boot, HikariCP pool init, and Flyway migration validation).
   - **Verification:** Captured command lines and PIDs in [`docs/audit/evidence/crash-test-process.txt`](file:///mnt/nexus/paycore/docs/audit/evidence/crash-test-process.txt).

2. **Database Integration Tests (`IdempotencyPostgresTransactionTest`):**
   - Runs against live PostgreSQL 16.6.
   - Verifies real database constraints (`uk_journals_scope_idempotency_key`), real transaction rollbacks, and atomic commit semantics across multiple connections.
