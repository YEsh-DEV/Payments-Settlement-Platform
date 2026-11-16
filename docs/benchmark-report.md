# Hot-Account Concurrency Benchmark Protocol & Provenance (Playbook Section 9)

**Status:** Benchmarks pending execution with real load generator against real Spring Boot ledger and PostgreSQL 16. Previous synthetic figures have been retracted (see [benchmark-provenance.md](audit/benchmark-provenance.md)).

---

## 1. Provenance Notice & Self-Audit Finding

An audit of `perf/run-benchmark.py` revealed that earlier benchmark figures were produced by an in-process Python formula (`simulate_strategy_latency`) using synthetic formulas (`random.expovariate` and arbitrary queue delay multipliers) rather than driving live HTTP traffic against the Java Spring Boot ledger and PostgreSQL database.

In accordance with WP-GATE-2B:
- All 240 synthetic JSON result files in `perf/results/` have been **deleted**.
- All synthetic throughput figures (such as 3,906 req/s) have been **retracted** from documentation and the ops console.
- All benchmark assets have been consolidated into `perf/k6/`.

---

## 2. Test Environment and Execution Protocol (Playbook Section 9.3)

The benchmark protocol defined in Playbook Section 9 requires driving live transactions against the running Spring Boot service:

- **Target Service:** Real `ledger-service` Spring Boot JVM artifact (`target/ledger-service-1.0.0-SNAPSHOT.jar`) running on `http://localhost:8082`.
- **Database:** PostgreSQL 16 (`localhost:5432`) with double-entry schema (`V1`, `V2`, `V3`, `V4`).
- **Load Generator:** `k6` executing native scripts in `perf/k6/`:
  - `workload-w1.js`: Uniform Captures (100% payment captures across distinct seller accounts).
  - `workload-w2.js`: Mixed Marketplace Flow (70% payment captures, 20% refunds, 10% payouts).
  - `workload-w3.js`: Pareto Hot-Spot (80% payment captures concentrated on top 20% merchants).
- **Concurrency Levels:** 1, 8, 32, 64, and 128 virtual users.
- **Duration per Run:** 30s warm-up (discarded) + 120s measurement window = 150 seconds per run.
- **Repeat Count:** 3 independent runs per cell; median reported for throughput and latency.
- **Integrity Validation:** Automated call to `GET /v1/integrity` after each run; any invariant violation marks the run invalid.
- **Decision Rule (Playbook Section 9.3):** Highest sustained throughput with p99 latency < 250 ms and zero invariant violations across all cells.

---

## 3. Benchmark Execution Matrix and Runtime Estimates

### Full Playbook Matrix
- 4 strategies (`S0_PESSIMISTIC`, `S1_LATE_LOCK`, `S2_STRIPED`, `S4_LEDGER_ONLY`)
- 3 workloads (`W1`, `W2`, `W3`)
- 5 concurrency tiers (`1`, `8`, `32`, `64`, `128`)
- 3 repeats per cell
- **Total runs:** $4 \times 3 \times 5 \times 3 = 180$ runs.
- **Total execution time at 150s/run:** $\mathbf{\sim 7.5\text{ hours}}$ (excluding database re-seeding).

### Proposed Focused Matrix (Alternative ADR)
To obtain empirical baselines without a multi-hour blockage:
- 4 strategies (`S0`, `S1`, `S2`, `S4`)
- 1 workload (`W2` mixed flow)
- 3 concurrency tiers (`8`, `32`, `64`)
- 1 run per cell (with 30s warm-up + 60s measurement = 90s per run)
- **Total runs:** $4 \times 1 \times 3 \times 1 = 12$ runs.
- **Total execution time:** $\mathbf{\sim 20\text{ minutes}}$.

Per WP-GATE-2B, execution of the multi-hour benchmark suite requires owner authorization of the proposed runtime.
