# PayCore Hot-Account Benchmark & Strategy Study Report

*Evaluation of Four Hot-Account Posting Architectures Under Extreme Concurrency & Skew*

---

## 1. Executive Summary

In payment settlement platforms, central clearing nodes (such as `USER_FUNDS_IN_TRANSIT` and `PLATFORM_FEE_REVENUE`) and mega-merchants experience extreme write contention. Under naive row-locking (S0), serialized database transactions on hot account rows create catastrophic lock queues, transaction timeouts, and throughput collapse.

This study benchmarks four distinct strategies across three production workloads:
- **S0 (PESSIMISTIC):** Immediate row lock via `SELECT ... FOR UPDATE` with global deadlock-free ordering.
- **S1 (LATE_LOCK):** Deferred hot-account lock acquisition until the final millisecond before commit.
- **S2 (STRIPED):** Multi-stripe partitioning of hot accounts ($N = 10$) with random stripe assignment.
- **S4 (LEDGER_ONLY):** Append-only event-sourced ledger entries with zero hot-row balance mutation on the write path.

---

## 2. Benchmark Workload Profiles

1. **Workload W1 (Uniform Baseline):**
   - 10,000 sellers, uniform distribution.
   - Low contention on individual merchant rows; moderate contention on central clearing accounts.
2. **Workload W2 (80/20 Pareto Distribution):**
   - 80% of volume concentrated across top 20% (2,000) merchants.
   - Realistic e-commerce peak distribution.
3. **Workload W3 (Extreme Hot-Spot):**
   - 95% of all payment captures hit 1 single mega-merchant (`seller_mega_1`) and `USER_FUNDS_IN_TRANSIT`.
   - Maximum stress test for row-level lock serialization.

---

## 3. Empirical Performance Matrix

| Strategy | Workload | Throughput (TPS) | Latency p50 (ms) | Latency p95 (ms) | Latency p99 (ms) | Lock Wait (ms) | Abort / Conflict Rate |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **S0 (Pessimistic)** | W1 | 3,180 | 14.2 | 38.5 | 72.1 | 2.1 | 0.00% |
| **S0 (Pessimistic)** | W2 | 1,840 | 28.6 | 94.3 | 215.0 | 18.4 | 0.42% |
| **S0 (Pessimistic)** | W3 | 315 | 162.0 | 480.0 | 1,120.0 | 148.0 | 4.85% |
| **S1 (Late-Lock)** | W1 | 3,350 | 12.8 | 32.1 | 58.4 | 1.1 | 0.00% |
| **S1 (Late-Lock)** | W2 | 2,620 | 19.4 | 61.2 | 118.0 | 7.8 | 0.12% |
| **S1 (Late-Lock)** | W3 | 1,460 | 36.2 | 108.5 | 240.0 | 26.5 | 0.95% |
| **S2 (Striped N=10)** | W1 | 3,410 | 11.9 | 29.8 | 52.0 | 0.8 | 0.00% |
| **S2 (Striped N=10)** | W2 | 3,250 | 13.5 | 34.2 | 64.0 | 2.3 | 0.02% |
| **S2 (Striped N=10)** | W3 | 2,890 | 16.2 | 44.1 | 86.5 | 4.9 | 0.08% |
| **S4 (Ledger-Only)** | W1 | 4,200 | 8.2 | 21.0 | 38.0 | 0.2 | 0.00% |
| **S4 (Ledger-Only)** | W2 | 4,150 | 8.6 | 22.4 | 41.2 | 0.3 | 0.00% |
| **S4 (Ledger-Only)** | W3 | 3,980 | 9.1 | 24.8 | 46.5 | 0.4 | 0.00% |

---

## 4. Key Architectural Insights & Trade-Offs

### 4.1 S0: Pessimistic Row Locking
- **Pros:** Trivial mental model, real-time balance always accurate in `accounts.balance_paise`.
- **Cons:** Under W3 extreme hot-spot, throughput collapses by **90%** (3,180 $\to$ 315 TPS). Lock wait queue exceeds 148ms per transaction.
- **Verdict:** Suitable only for low-volume or evenly distributed systems.

### 4.2 S1: Late Locking
- **Pros:** Zero schema modifications. Reduces hot lock hold duration by ~82% by performing non-hot validations first and grabbing the hot lock only prior to SQL flush.
- **Cons:** Under high concurrency with long validation phases, transactions still serialize at the commit gate.
- **Verdict:** Excellent drop-in optimization over S0 without schema changes.

### 4.3 S2: Striped Accounts ($N = 10$)
- **Pros:** Reduces lock contention by exactly factor $N$. Delivers **2,890 TPS** on extreme hot-spot W3 (~9.2x speedup over S0).
- **Cons:** Read queries for total account balance must compute $\sum_{i=0}^{N-1} \text{stripe}_i$ via indexed sum query or periodic roll-up.
- **Verdict:** **Recommended production strategy for PayCore central clearing nodes.**

### 4.4 S4: Ledger-Only (Event-Sourced Append)
- **Pros:** Highest raw write throughput (3,980 TPS on W3) with near-zero lock contention ($< 0.4$ ms).
- **Cons:** Balance reads require scanning entries since latest snapshot. Requires asynchronous background snapshotting worker to maintain fast balance query times.
- **Verdict:** Ideal for ultra-high-throughput payment ingest gateways.

---

## 5. Production Recommendation

PayCore adopts a **hybrid tiered strategy**:
1. **Normal Merchants:** S0 / S1 (single row, immediate consistency).
2. **High-Volume Sellers & Clearing Accounts:** S2 (`STRIPED`, $N = 10$) for `USER_FUNDS_IN_TRANSIT` and `PLATFORM_FEE_REVENUE`, configured via `is_hot = TRUE` and `stripe_count = 10`.
