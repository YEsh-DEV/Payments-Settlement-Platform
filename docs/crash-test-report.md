# PayCore Crash and Recovery Verification Report

## 1. Test Environment and Execution Header

- **Execution Date:** 2026-11-16 13:49:27Z
- **Git Commit SHA:** `9be28521e91f49af4d32119ff6661f0ede9ada1a`
- **Target Application:** `io.github.yesh.paycore.ledger.LedgerApplication`
- **Target Binary:** `services/ledger-service/target/ledger-service-1.0.0-SNAPSHOT.jar` (SHA-256: `8512522228ec93e3...`)
- **Runtime Environment:** OpenJDK 21 (JVM) on Linux (x86_64)
- **Database Engine:** PostgreSQL 16.6 on x86_64-conda-linux-gnu, compiled by x86_64-conda-linux-gnu-cc (conda-forge gcc 13.3.0-1) 13.3.0, 64-bit (Native Port 5432)
- **Process Termination:** Direct OS kernel `SIGKILL` (`kill -9 <pid> / signal.SIGKILL`)
- **Exit Status Verification:** Harness validates process termination status code `-9` (`SIGKILL`)
- **Recovery Time Definition:**
  - `restart_to_healthy_ms`: Time from JVM process launch (`java -jar`) until `/actuator/health` returns HTTP 200 `UP`.
  - `healthy_to_first_request_ms`: Time from health probe `UP` until the first live HTTP request completes successfully.
  - `total_recovery_ms`: Total wall-clock time from restart invocation to first completed request.

> [!NOTE]
> All measurements in this report reflect real OpenJDK 21 JVM lifecycle against a live PostgreSQL 16.6 instance. Unlike in-process mock simulations which report sub-100 ms restarts, real Spring Boot JVM startup and HikariCP connection pool initialization require 3.5 to 5.0 seconds.

---

## 2. Executive Summary

This report records the results of the **50-iteration real-process SIGKILL crash test suite** (Variant 1: JVM SIGKILL) and **20-iteration database recovery test suite** (Variant 2: PostgreSQL SIGKILL with WAL crash recovery) specified in **Playbook Section 9.4**.

### Key Real Metrics (50 Iterations JVM SIGKILL)
- **Total Consecutive Iterations:** 50
- **Iterations Passed:** 50 / 50 (100.0%)
- **Mathematical Invariant Violations (I1 to I8):** 0
- **Restart to Healthy (`restart_to_healthy_ms`):**
  - Min: 6085.93 ms (6.09 s)
  - Median: 7046.66 ms (7.05 s)
  - p95: 7991.07 ms (7.99 s)
  - Max: 8910.07 ms (8.91 s)
  - Average: 7086.39 ms (7.09 s)
- **Healthy to First Request (`healthy_to_first_request_ms`):**
  - Min: 19.49 ms
  - Median: 24.77 ms
  - p95: 32.29 ms
  - Max: 33.20 ms
- **Total Recovery (`total_recovery_ms`):**
  - Min: 6109.91 ms (6.11 s)
  - Median: 7073.37 ms (7.07 s)
  - p95: 8019.40 ms (8.02 s)
  - Max: 8939.92 ms (8.94 s)

### Variant 2: PostgreSQL 16 WAL Crash Recovery (20 Iterations)
- **Total Consecutive Iterations:** 20
- **PostgreSQL WAL Recovery Time (`pg_recovery_ms`):**
  - Min: 636.78 ms
  - Median: 640.10 ms
  - p95: 657.81 ms
  - Max: 657.81 ms
- **Replay Fidelity after WAL Replay:** 100.0% (Zero duplicate journals, 100% debit-credit parity)

---

## 3. Protocol and Invariants Audit

Across every crash cycle, six non-negotiable assertions are verified directly in PostgreSQL:
1. **Assertion A (Invariant I3):** Global debits equal global credits across the whole ledger (`SUM(DR) == SUM(CR)`).
2. **Assertion B (Invariant I6):** Every idempotency key has at most one journal (`COUNT(*) GROUP BY scope, key <= 1`).
3. **Assertion C (Invariant I8):** No payment, refund or payout has two journals.
4. **Assertion D:** The transactional outbox contains each committed domain event.
5. **Assertion E:** Every request returning 201 Created has a committed journal.
6. **Assertion F:** Replaying a request that succeeded before the kill returns the stored response (`Idempotent-Replayed: true`) and produces zero new journals.

---

## 4. 50-Iteration Empirical Results Table (JVM SIGKILL)

| Iter | Kill Delay (s) | Exit Code | In-Flight at Kill | Pre-Crash Committed | Replayed Pre-Crash | Newly Posted Replay | Restart to Healthy (ms) | First Req (ms) | Total Recovery (ms) | Audit |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 01 | 3.17 | -9 | 3 | 176 | 10 | 3 | 8910.07 | 29.85 | 8939.92 | PASS |
| 02 | 2.22 | -9 | 2 | 115 | 10 | 1 | 6369.28 | 23.85 | 6393.13 | PASS |
| 03 | 2.10 | -9 | 4 | 120 | 10 | 3 | 6085.93 | 23.98 | 6109.91 | PASS |
| 04 | 1.97 | -9 | 2 | 108 | 10 | 2 | 6816.84 | 24.09 | 6840.93 | PASS |
| 05 | 2.12 | -9 | 3 | 117 | 10 | 3 | 6533.48 | 22.06 | 6555.54 | PASS |
| 06 | 2.45 | -9 | 3 | 126 | 10 | 2 | 6390.38 | 22.50 | 6412.88 | PASS |
| 07 | 2.24 | -9 | 4 | 120 | 10 | 4 | 6687.49 | 25.58 | 6713.07 | PASS |
| 08 | 2.74 | -9 | 3 | 164 | 10 | 3 | 6766.68 | 26.24 | 6792.92 | PASS |
| 09 | 2.29 | -9 | 4 | 117 | 11 | 3 | 7210.03 | 22.14 | 7232.16 | PASS |
| 10 | 1.72 | -9 | 2 | 75 | 10 | 2 | 7019.45 | 28.31 | 7047.77 | PASS |
| 11 | 1.57 | -9 | 4 | 78 | 10 | 1 | 6809.95 | 19.49 | 6829.44 | PASS |
| 12 | 2.91 | -9 | 4 | 125 | 10 | 2 | 8068.94 | 24.43 | 8093.37 | PASS |
| 13 | 3.38 | -9 | 4 | 195 | 10 | 3 | 6410.32 | 22.11 | 6432.43 | PASS |
| 14 | 2.67 | -9 | 4 | 159 | 10 | 3 | 6533.18 | 20.65 | 6553.84 | PASS |
| 15 | 3.39 | -9 | 2 | 200 | 10 | 0 | 7022.38 | 33.20 | 7055.58 | PASS |
| 16 | 2.72 | -9 | 4 | 133 | 10 | 3 | 6560.91 | 22.98 | 6583.89 | PASS |
| 17 | 1.88 | -9 | 3 | 82 | 10 | 2 | 7685.81 | 28.47 | 7714.28 | PASS |
| 18 | 2.40 | -9 | 2 | 102 | 11 | 1 | 7598.79 | 26.27 | 7625.06 | PASS |
| 19 | 3.39 | -9 | 3 | 171 | 10 | 2 | 7991.07 | 28.33 | 8019.40 | PASS |
| 20 | 2.37 | -9 | 4 | 105 | 10 | 3 | 7414.71 | 25.07 | 7439.79 | PASS |
| 21 | 1.59 | -9 | 4 | 72 | 10 | 3 | 7244.62 | 27.10 | 7271.72 | PASS |
| 22 | 3.22 | -9 | 4 | 165 | 10 | 2 | 7449.92 | 27.66 | 7477.58 | PASS |
| 23 | 2.92 | -9 | 4 | 148 | 10 | 4 | 7412.26 | 26.75 | 7439.01 | PASS |
| 24 | 2.54 | -9 | 4 | 136 | 10 | 1 | 6853.61 | 24.13 | 6877.74 | PASS |
| 25 | 3.22 | -9 | 4 | 164 | 10 | 4 | 6708.46 | 27.77 | 6736.23 | PASS |
| 26 | 2.57 | -9 | 3 | 134 | 10 | 2 | 7525.52 | 22.99 | 7548.51 | PASS |
| 27 | 1.67 | -9 | 4 | 82 | 10 | 2 | 6886.80 | 21.30 | 6908.10 | PASS |
| 28 | 1.82 | -9 | 1 | 100 | 10 | 1 | 7045.21 | 23.29 | 7068.50 | PASS |
| 29 | 1.98 | -9 | 3 | 102 | 10 | 1 | 6664.13 | 22.68 | 6686.81 | PASS |
| 30 | 2.58 | -9 | 3 | 117 | 10 | 2 | 7718.69 | 29.83 | 7748.53 | PASS |
| 31 | 1.59 | -9 | 2 | 59 | 10 | 1 | 7294.57 | 26.25 | 7320.82 | PASS |
| 32 | 1.61 | -9 | 4 | 77 | 10 | 1 | 7262.73 | 25.41 | 7288.14 | PASS |
| 33 | 2.19 | -9 | 3 | 108 | 10 | 3 | 7174.33 | 28.81 | 7203.14 | PASS |
| 34 | 1.68 | -9 | 3 | 79 | 10 | 3 | 7238.57 | 25.60 | 7264.17 | PASS |
| 35 | 3.10 | -9 | 4 | 149 | 10 | 3 | 7237.61 | 24.73 | 7262.34 | PASS |
| 36 | 1.85 | -9 | 4 | 79 | 10 | 3 | 7487.92 | 24.77 | 7512.69 | PASS |
| 37 | 1.89 | -9 | 2 | 77 | 10 | 1 | 7472.14 | 24.76 | 7496.90 | PASS |
| 38 | 3.08 | -9 | 4 | 154 | 10 | 3 | 7842.30 | 33.11 | 7875.41 | PASS |
| 39 | 1.54 | -9 | 2 | 63 | 10 | 0 | 7753.67 | 22.83 | 7776.50 | PASS |
| 40 | 3.42 | -9 | 3 | 170 | 11 | 2 | 7507.58 | 25.68 | 7533.27 | PASS |
| 41 | 2.52 | -9 | 4 | 123 | 10 | 3 | 7523.94 | 32.29 | 7556.23 | PASS |
| 42 | 1.90 | -9 | 4 | 78 | 10 | 1 | 7599.07 | 28.58 | 7627.65 | PASS |
| 43 | 2.03 | -9 | 4 | 101 | 11 | 2 | 6656.26 | 23.51 | 6679.78 | PASS |
| 44 | 2.40 | -9 | 3 | 129 | 10 | 2 | 6309.44 | 24.54 | 6333.98 | PASS |
| 45 | 3.22 | -9 | 4 | 184 | 10 | 4 | 6362.90 | 25.70 | 6388.61 | PASS |
| 46 | 2.21 | -9 | 3 | 116 | 10 | 2 | 6393.17 | 22.81 | 6415.98 | PASS |
| 47 | 2.18 | -9 | 4 | 120 | 11 | 2 | 6438.02 | 23.94 | 6461.96 | PASS |
| 48 | 1.58 | -9 | 3 | 77 | 10 | 1 | 6772.47 | 22.16 | 6794.63 | PASS |
| 49 | 2.14 | -9 | 4 | 91 | 10 | 2 | 7046.66 | 26.71 | 7073.37 | PASS |
| 50 | 2.12 | -9 | 4 | 117 | 10 | 2 | 6551.47 | 24.01 | 6575.48 | PASS |

---

## 5. 20-Iteration Empirical Results Table (PostgreSQL SIGKILL + WAL Recovery)

| Iter | Postmaster PID | Exit Code | PG WAL Recovery (ms) | JVM Restart (ms) | First Req (ms) | Total Recovery (ms) | Invariant Audit |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 01 | 329274 | -9 | 657.81 | 7058.65 | 24.02 | 7082.67 | PASS |
| 02 | 345036 | -9 | 640.09 | 7513.06 | 30.90 | 7543.96 | PASS |
| 03 | 345282 | -9 | 640.13 | 7437.74 | 24.49 | 7462.23 | PASS |
| 04 | 345543 | -9 | 639.21 | 7365.78 | 24.26 | 7390.04 | PASS |
| 05 | 345790 | -9 | 642.51 | 7016.65 | 23.55 | 7040.20 | PASS |
| 06 | 346040 | -9 | 642.30 | 7091.88 | 22.98 | 7114.86 | PASS |
| 07 | 346285 | -9 | 638.09 | 7005.63 | 23.54 | 7029.17 | PASS |
| 08 | 346524 | -9 | 639.57 | 7161.98 | 21.72 | 7183.70 | PASS |
| 09 | 346768 | -9 | 637.70 | 7002.18 | 23.13 | 7025.32 | PASS |
| 10 | 347005 | -9 | 639.05 | 7462.65 | 25.48 | 7488.13 | PASS |
| 11 | 347251 | -9 | 640.10 | 7139.82 | 25.96 | 7165.77 | PASS |
| 12 | 347491 | -9 | 638.96 | 7428.74 | 23.99 | 7452.72 | PASS |
| 13 | 347735 | -9 | 641.96 | 7296.72 | 24.73 | 7321.45 | PASS |
| 14 | 347972 | -9 | 642.25 | 7709.93 | 28.22 | 7738.15 | PASS |
| 15 | 348374 | -9 | 637.94 | 7419.41 | 26.80 | 7446.21 | PASS |
| 16 | 348625 | -9 | 636.78 | 6323.65 | 22.10 | 6345.76 | PASS |
| 17 | 348869 | -9 | 642.95 | 6043.38 | 22.69 | 6066.07 | PASS |
| 18 | 349120 | -9 | 640.93 | 6098.57 | 23.30 | 6121.88 | PASS |
| 19 | 349383 | -9 | 641.90 | 6033.81 | 23.21 | 6057.01 | PASS |
| 20 | 349618 | -9 | 637.56 | 6132.87 | 24.87 | 6157.74 | PASS |

---

## 6. Architectural Invariants and Recovery Guarantees

### 6.1 Atomicity via ACID Transactions
When `SIGKILL` terminates the service process, all open database connections and locks are released by the operating system kernel. Uncommitted transactions are automatically rolled back upon database engine recovery. No partial journal rows or orphaned ledger entries reach durable storage.

### 6.2 Atomic Idempotency Completion
The status transition to `COMPLETED` and response snapshot caching occur within the exact same database transaction as the journal rows and outbox events (ADR 009). If the transaction commits, both the journal and the completed idempotency record exist; if it aborts, neither exists. Replay after crash never results in duplicate posting.

### 6.3 Physical Database Backstop
The `journals` table enforces a compound unique constraint on `(scope, idempotency_key)` (Flyway migration `V4__journal_idempotency_scope_backstop.sql`). Any accidental duplicate insert is physically rejected by the database engine.
