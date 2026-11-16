# PayCore Hot-Account Strategy Study Report

Evaluation of four hot-account posting architectures under concurrency and account skew (Playbook Section 9).

---

## 1. Executive Summary

Central clearing accounts in payment settlement platforms (such as `USER_FUNDS_IN_TRANSIT` and `PLATFORM_FEE_REVENUE`) and high-volume merchant accounts experience high write contention. Under naive row locking (S0), serialized database transactions on hot account rows create lock queues, high latency, and throughput collapse.

This study benchmarks four distinct strategies defined in Playbook Section 9.1:
- **S0 (PESSIMISTIC):** Immediate row lock via `SELECT ... FOR UPDATE` with global deadlock-free ordering.
- **S1 (LATE_LOCK):** Deferred hot-account lock acquisition until right before SQL commit.
- **S2 (STRIPED):** Multi-stripe partitioning of hot accounts ($N = 16$) with random stripe assignment.
- **S4 (LEDGER_ONLY):** Append-only event-sourced ledger entries with zero hot-row balance mutation on the write path.

The full benchmark methodology, 240 benchmark result runs, and raw JSON measurements are documented in [benchmark-report.md](file:///mnt/nexus/paycore/docs/benchmark-report.md) and [benchmark-audit.md](file:///mnt/nexus/paycore/docs/audit/benchmark-audit.md).

---

## 2. Benchmark Workloads and Execution Protocol

The benchmark suite implements three workloads specified in Playbook Section 9.2:
- **W1 (Uniform Captures):** 100% payment captures across distinct seller accounts.
- **W2 (Mixed Marketplace Flow):** 70% payment captures, 20% refunds, 10% payout requests.
- **W3 (Pareto Hot-Spot):** 80% of payment captures concentrated on 20% of sellers.

Each cell was evaluated across 5 concurrency levels ($c \in \{1, 8, 32, 64, 128\}$) with 3 independent test runs per cell (reporting the median).

---

## 3. Measured Performance Summary

### Peak Sustained Throughput (req/s)

| Strategy | W1 (Uniform) | W2 (Mixed Flow) | W3 (Pareto) |
| :--- | :---: | :---: | :---: |
| **S0 (Pessimistic)** | 922.6 | 990.5 | 1,037.8 |
| **S1 (Late-Lock)** | 1,965.1 | 2,129.9 | 2,221.2 |
| **S2 (Striped, N=16)** | **3,816.4** | **3,905.6** | **3,899.7** |
| **S4 (Ledger-Only)** | 3,791.6 | 3,731.3 | 3,755.5 |

### Latency and Conflict Profile at Concurrency 128 (Workload W2)

| Strategy | Throughput (req/s) | p50 Latency (ms) | p95 Latency (ms) | p99 Latency (ms) | Abort Rate (%) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **S0 (Pessimistic)** | 223.7 | 70.24 | 114.09 | 150.16 | 0.00% |
| **S1 (Late-Lock)** | 792.3 | 19.97 | 30.44 | 37.80 | 5.51% |
| **S2 (Striped, N=16)** | **3,547.3** | **3.91** | **8.37** | **11.61** | **0.00%** |
| **S4 (Ledger-Only)** | 2,819.7 | 5.12 | 9.09 | 12.23 | 0.00% |

---

## 4. Architectural Analysis and Trade-Offs

### 4.1 S0: Pessimistic Row Locking
- **Mechanism:** Acquires row-level exclusive locks (`FOR UPDATE`) in deterministic account ID order during the transaction.
- **Trade-off:** Strong immediate consistency, but throughput drops to 223.7 req/s at $c=128$ due to lock queues. Lock queuing causes p99 latency to rise to 150.16 ms.
- **Conclusion:** Not suitable for high-throughput clearing accounts.

### 4.2 S1: Late Locking
- **Mechanism:** Executes non-hot account modifications and invariant checks first; acquires exclusive lock on the hot clearing account only immediately prior to transaction commit.
- **Trade-off:** Reduces lock hold duration and boosts throughput to 2,129.9 req/s at $c=32$. However, under extreme concurrency ($c=128$), conflicting transactions attempting to acquire the hot row lock cause serialization failures (5.51% abort rate).
- **Conclusion:** Effective at moderate concurrency; unacceptable conflict rate at peak concurrency.

### 4.3 S2: Striped Accounts ($N = 16$)
- **Mechanism:** Divides hot clearing accounts into $N = 16$ independent balance stripe rows (`account_stripes`). Write transactions pick a pseudo-random stripe using bitwise masking (`stripe_index = thread_hash & 15`). Read balance queries compute $\sum \text{stripe\_balance}$.
- **Trade-off:** Reduces hot row lock contention by a factor of 16. Achieves 3,905.6 req/s peak sustained throughput and maintains 3,547.3 req/s at $c=128$ with p99 latency of 11.61 ms and 0.00% aborts.
- **Conclusion:** Meets all criteria of the Playbook Section 9.3 Decision Rule (highest throughput, p99 < 250 ms, zero invariant violations). Selected as the production ledger posting strategy in ADR 008.

### 4.4 S4: Ledger-Only (Event-Sourced Append)
- **Mechanism:** Writes immutable journal and entry rows without updating balance rows in the critical transaction path. Account balances are derived asynchronously by background snapshot aggregators.
- **Trade-off:** Achieves 3,731.3 req/s peak throughput with zero row locking on balances. However, point-in-time balance lookups require reading un-snapshotted journal entries or accepting eventual consistency.
- **Conclusion:** High write throughput, but adds operational complexity for real-time payout validations that require immediate available balance verification.

---

## 5. Strategy Decision

In accordance with ADR 008 and Playbook Section 9.3:
- Strategy is configured at application startup via `paycore.ledger.strategy=S2_STRIPED`.
- Stripe allocation is defined in schema migration `V2__striped_accounts.sql` with $N = 16$ stripes.
- Balance verification for striped accounts computes the aggregate balance across all 16 stripes, satisfying Invariant I4.
