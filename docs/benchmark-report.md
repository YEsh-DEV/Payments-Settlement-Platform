# PayCore Hot-Account Benchmark Empirical Report

**Document Status:** PENDING REAL RUN  
**Reference Specification:** `PayCore Build Playbook.md`, Section 9  
**Execution Environment:** Pending Docker Compose disk-backed execution (Step 7)  

---

## 1. Executive Summary

This report will record empirical measurements from the **Tier 1 Benchmark Matrix** (72 runs across 4 strategies, 2 workloads, 3 concurrency levels, and 3 repeats) as mandated by Playbook Section 9.

### Strategy Definitions Under Test
- **S0 (PESSIMISTIC):** Full row lock on hot accounts (`PLATFORM_FEE_REVENUE`, `PROVIDER_RECEIVABLE`) at transaction start (`S0PessimisticStrategy.java`).
- **S1 (LATE_LOCK):** Locks non-hot accounts first; defers hot-account balance mutation to immediately before commit (`S1LateLockStrategy.java`).
- **S2 (STRIPED):** Partitions hot-account balances across $N=16$ stripes, reducing row lock contention by $16\times$ (`S2StripedStrategy.java`).
- **S4 (LEDGER_ONLY):** Eliminates hot-account balance row locks entirely by appending immutable ledger entries with snapshot reconciliation (`S4LedgerOnlyStrategy.java`).

---

## 2. Benchmark Results Table

*Status: Pending real run execution following Docker access activation (Step 7).*

| Strategy | Concurrency (VUs) | Throughput (req/s) | p50 Latency (ms) | p95 Latency (ms) | p99 Latency (ms) | Abort Rate (%) | Invariants (I1..I8) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **S0_PESSIMISTIC** | 8 | *Pending real run* | - | - | - | - | - |
| **S0_PESSIMISTIC** | 32 | *Pending real run* | - | - | - | - | - |
| **S0_PESSIMISTIC** | 128 | *Pending real run* | - | - | - | - | - |
| **S1_LATE_LOCK** | 8 | *Pending real run* | - | - | - | - | - |
| **S1_LATE_LOCK** | 32 | *Pending real run* | - | - | - | - | - |
| **S1_LATE_LOCK** | 128 | *Pending real run* | - | - | - | - | - |
| **S2_STRIPED** | 8 | *Pending real run* | - | - | - | - | - |
| **S2_STRIPED** | 32 | *Pending real run* | - | - | - | - | - |
| **S2_STRIPED** | 128 | *Pending real run* | - | - | - | - | - |
| **S4_LEDGER_ONLY** | 8 | *Pending real run* | - | - | - | - | - |
| **S4_LEDGER_ONLY** | 32 | *Pending real run* | - | - | - | - | - |
| **S4_LEDGER_ONLY** | 128 | *Pending real run* | - | - | - | - | - |

---

## 3. Protocol Verification Criteria for Final Numbers

Final numbers will be populated exclusively from JSON result files in `perf/results/` that satisfy all seven protocol conditions:
1. Target service is the real OpenJDK 21 Spring Boot JVM (`services/ledger-service-1.0.0-SNAPSHOT.jar`).
2. Storage is disk-backed NVMe/SSD (never tmpfs or RAM disk).
3. Database snapshot is cleanly dropped and restored with 10,000 pre-created sellers before every cell.
4. Each cell executes 30s warm-up (unmetered) + 120s measured.
5. Three independent repeats per cell with median reported.
6. Zero invariant violations on `GET /v1/integrity` following each run.
7. System telemetry (CPU, memory, kernel, PostgreSQL parameters) recorded in raw JSON.
