# Hot-Account Benchmark Report (Playbook Section 9)

## 1. Test Environment and Execution Protocol

- **Operating System:** Linux 7.2.15-omarchy-x86_64
- **Compute Hardware:** AMD Ryzen / Intel Xeon (12 cores, 32 GB RAM)
- **Runtime:** OpenJDK 64-Bit Server VM (build 21.0.13+11-LTS, mixed mode)
- **Database & Toolchain:** PostgreSQL 16 double-entry ledger schema, Maven 3.10.0
- **Workload Definitions (Playbook Section 9.2):**
  - **W1 (Uniform Captures):** 100% payment captures across distinct seller accounts.
  - **W2 (Mixed Marketplace Flow):** 70% payment captures, 20% refunds, 10% payout requests.
  - **W3 (Pareto Hot-Spot):** 80% of payment captures concentrated on 20% of sellers.
- **Concurrency Levels:** 1, 8, 32, 64, and 128 virtual users / concurrent threads.
- **Measurement Protocol:** 3 runs per cell; median reported for throughput and latency. Each run verified by continuous integrity audit.
- **Decision Rule (Playbook Section 9.3):** Highest sustained throughput with p99 latency < 250 ms and zero invariant violations.

## 2. Workload W1: Uniform Payment Captures (100% Captures)

### 2.1 Sustained Throughput (req/s, higher is better)

| Strategy | c=1 | c=8 | c=32 | c=64 | c=128 | Peak Sustained |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| S0_PESSIMISTIC | 218.9 | 922.6 | 711.7 | 388.1 | 203.8 | **922.6** |
| S1_LATE_LOCK | 269.2 | 1,735.5 | 1,965.1 | 1,265.8 | 730.2 | **1,965.1** |
| S2_STRIPED | 250.4 | 1,954.9 | 3,816.4 | 3,682.6 | 3,475.4 | **3,816.4** |
| S4_LEDGER_ONLY | 269.8 | 2,032.3 | 3,791.6 | 3,404.4 | 2,784.7 | **3,791.6** |

### 2.2 Latency Distribution (ms at c=32 and c=128)

| Strategy | c=32 p50 | c=32 p95 | c=32 p99 | c=128 p50 | c=128 p95 | c=128 p99 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| S0_PESSIMISTIC | 20.59 | 34.98 | 48.66 | 72.85 | 114.65 | 143.07 |
| S1_LATE_LOCK | 7.42 | 12.87 | 15.08 | 20.41 | 30.93 | 38.38 |
| S2_STRIPED | 3.54 | 8.57 | 11.05 | 3.99 | 8.66 | 12.21 |
| S4_LEDGER_ONLY | 3.67 | 7.76 | 10.75 | 5.19 | 9.81 | 12.72 |

### 2.3 Abort Rate (%) and Error Classification

| Strategy | c=1 | c=8 | c=32 | c=64 | c=128 | Primary Abort Cause |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| S0_PESSIMISTIC | 0.0% | 0.0% | 0.0% | 0.0% | 0.0% | None (0.0% aborts; lock queuing) |
| S1_LATE_LOCK | 0.0% | 0.0% | 0.31% | 1.25% | 6.76% | Serialization conflict on deferred hot row lock |
| S2_STRIPED | 0.0% | 0.0% | 0.0% | 0.0% | 0.0% | None (0.0% aborts; writes spread across 16 stripes) |
| S4_LEDGER_ONLY | 0.0% | 0.0% | 0.0% | 0.0% | 0.0% | None (0.0% aborts; append-only entries) |

## 2. Workload W2: Mixed Marketplace Flow (70% Captures, 20% Refunds, 10% Payouts)

### 2.1 Sustained Throughput (req/s, higher is better)

| Strategy | c=1 | c=8 | c=32 | c=64 | c=128 | Peak Sustained |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| S0_PESSIMISTIC | 218.5 | 990.5 | 746.3 | 412.0 | 223.7 | **990.5** |
| S1_LATE_LOCK | 271.8 | 1,744.9 | 2,129.9 | 1,343.4 | 792.3 | **2,129.9** |
| S2_STRIPED | 263.0 | 1,964.1 | 3,905.6 | 3,750.2 | 3,547.3 | **3,905.6** |
| S4_LEDGER_ONLY | 272.1 | 2,072.6 | 3,731.3 | 3,412.9 | 2,819.7 | **3,731.3** |

### 2.2 Latency Distribution (ms at c=32 and c=128)

| Strategy | c=32 p50 | c=32 p95 | c=32 p99 | c=128 p50 | c=128 p95 | c=128 p99 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| S0_PESSIMISTIC | 20.51 | 34.8 | 45.54 | 70.24 | 114.09 | 150.16 |
| S1_LATE_LOCK | 7.12 | 11.41 | 14.61 | 19.97 | 30.44 | 37.8 |
| S2_STRIPED | 3.5 | 7.81 | 11.33 | 3.91 | 8.37 | 11.61 |
| S4_LEDGER_ONLY | 3.73 | 8.11 | 10.79 | 5.12 | 9.09 | 12.23 |

### 2.3 Abort Rate (%) and Error Classification

| Strategy | c=1 | c=8 | c=32 | c=64 | c=128 | Primary Abort Cause |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| S0_PESSIMISTIC | 0.0% | 0.0% | 0.0% | 0.0% | 0.0% | None (0.0% aborts; lock queuing) |
| S1_LATE_LOCK | 0.0% | 0.0% | 0.16% | 1.64% | 5.51% | Serialization conflict on deferred hot row lock |
| S2_STRIPED | 0.0% | 0.0% | 0.0% | 0.0% | 0.0% | None (0.0% aborts; writes spread across 16 stripes) |
| S4_LEDGER_ONLY | 0.0% | 0.0% | 0.0% | 0.0% | 0.0% | None (0.0% aborts; append-only entries) |

## 2. Workload W3: Pareto Hot-Spot (80% Traffic to Top 20% Merchants)

### 2.1 Sustained Throughput (req/s, higher is better)

| Strategy | c=1 | c=8 | c=32 | c=64 | c=128 | Peak Sustained |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| S0_PESSIMISTIC | 235.2 | 1,037.8 | 818.6 | 453.5 | 246.6 | **1,037.8** |
| S1_LATE_LOCK | 260.1 | 1,782.7 | 2,221.2 | 1,435.3 | 853.0 | **2,221.2** |
| S2_STRIPED | 268.7 | 2,013.7 | 3,899.7 | 3,798.1 | 3,599.8 | **3,899.7** |
| S4_LEDGER_ONLY | 292.4 | 2,022.3 | 3,755.5 | 3,408.8 | 2,823.7 | **3,755.5** |

### 2.2 Latency Distribution (ms at c=32 and c=128)

| Strategy | c=32 p50 | c=32 p95 | c=32 p99 | c=128 p50 | c=128 p95 | c=128 p99 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| S0_PESSIMISTIC | 19.81 | 33.7 | 42.6 | 68.58 | 111.79 | 141.64 |
| S1_LATE_LOCK | 6.89 | 12.27 | 15.66 | 19.4 | 30.15 | 37.33 |
| S2_STRIPED | 3.45 | 8.19 | 12.36 | 3.85 | 8.34 | 11.46 |
| S4_LEDGER_ONLY | 3.71 | 7.73 | 11.56 | 5.13 | 9.24 | 12.41 |

### 2.3 Abort Rate (%) and Error Classification

| Strategy | c=1 | c=8 | c=32 | c=64 | c=128 | Primary Abort Cause |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| S0_PESSIMISTIC | 0.0% | 0.0% | 0.0% | 0.0% | 0.0% | None (0.0% aborts; lock queuing) |
| S1_LATE_LOCK | 0.0% | 0.0% | 0.47% | 1.48% | 5.12% | Serialization conflict on deferred hot row lock |
| S2_STRIPED | 0.0% | 0.0% | 0.0% | 0.0% | 0.0% | None (0.0% aborts; writes spread across 16 stripes) |
| S4_LEDGER_ONLY | 0.0% | 0.0% | 0.0% | 0.0% | 0.0% | None (0.0% aborts; append-only entries) |

## 3. Post-Run Ledger Integrity Verification

In accordance with Playbook Section 9.2, every benchmark run was followed immediately by continuous invariant verification against `GET /v1/integrity`:

| Invariant ID | Description | Verified Count | Violations Detected | Status |
| :--- | :--- | :---: | :---: | :---: |
| **I1** | Double-Entry Balance: $\ge 2$ legs, positive amounts, $\sum DR = \sum CR$ | All sample journals | 0 | **PASS** |
| **I2** | Ledger Entry Immutability: database trigger rejects UPDATE/DELETE | 100% entries | 0 | **PASS** |
| **I3** | Global Zero-Sum: $\sum DR = \sum CR$ across all entries | System-wide sum | 0 | **PASS** |
| **I4** | Balance Correctness: cached balance equals sum of entries (S2: sum of stripes) | 100% accounts | 0 | **PASS** |

## 4. Architectural Analysis and Strategy Selection

### 4.1 Comparative Analysis
1. **S0_PESSIMISTIC (Baseline):** Demonstrates strong consistency and zero aborts, but throughput degrades sharply past 8 concurrent users due to row lock serialization on hot platform accounts (`PLATFORM_FEE_REVENUE`, `PROVIDER_RECEIVABLE`). p99 latency exceeds 140 ms at c=128.
2. **S1_LATE_LOCK:** Deferring hot account lock acquisition until immediately before commit reduces the lock hold window, achieving higher throughput up to 32 virtual users. However, under high concurrency (64 to 128 VUs), concurrent transactions competing to commit against the same hot row induce serialization failures and lock wait aborts (5.1% to 6.8% abort rate), violating the zero-unexplained-abort requirement.
3. **S2_STRIPED (Default Production Strategy):** Distributes hot platform account balance mutations across $N = 16$ deterministic stripes (`hash(journalId) % 16`). Peak sustained throughput exceeds **3,800 req/s** with p99 latency under **12.5 ms** and **0.0% aborts** across all concurrency levels. Live balances are immediately readable via an indexed sum query (`sumBalanceByAccountId`).
4. **S4_LEDGER_ONLY:** Appends immutable ledger entries without updating live account rows. Delivers high write throughput (~3,700 req/s) with 0.0% aborts, but requires asynchronous snapshot reconstruction for balance queries.

### 4.2 Decision Rule Outcome
Applying Playbook Section 9.3 decision criteria (**highest sustained throughput with p99 < 250 ms and zero integrity violations**):
- **Selected Strategy:** `S2_STRIPED` is designated as the primary production posting engine strategy (`paycore.ledger.strategy=S2_STRIPED`).
- **Verification:** Both S2 and S4 satisfy the latency threshold (<250 ms) and zero violation requirement. S2 is preferred for production because it supports immediate, single-query account balance reconciliation without background snapshot latency.

## 5. Provenance and Raw Data Artifacts

All numbers in this report are compiled directly from the 180 raw JSON benchmark outputs generated by `perf/run-benchmark.py` and stored in `perf/results/`:
- Raw benchmark results directory: `perf/results/*.json`
- Generator script: `scripts/build-benchmark-report.py`
- Execution timestamp: 2026-11-15T15:39:33Z
