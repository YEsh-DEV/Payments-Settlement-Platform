# PayCore Benchmark Provenance Audit (Playbook Section 9)

**Date:** 2026-11-16  
**Auditor:** PayCore Engineering  
**Subject:** Provenance investigation of `perf/results/*.json`, `perf/run-benchmark.py`, and reported throughput numbers.

---

## 1. Questions and Findings

### 1. What was the benchmark load generator?
**Finding:** The benchmark runner was `perf/run-benchmark.py`. It did **not** use `k6` or make HTTP calls to a running service.
**Source Evidence (`perf/run-benchmark.py` lines 1-25):**
```python
def simulate_strategy_latency(strategy, concurrency, is_hot_contention=False):
    base_latency_ms = 1.5 + random.uniform(0.1, 0.8)
    if strategy == "S0_PESSIMISTIC":
        queue_delay = (concurrency * 0.45) if is_hot_contention else (concurrency * 0.05)
        latency = base_latency_ms + queue_delay + random.expovariate(1.0 / (2.0 + queue_delay * 0.3))
    elif strategy == "S1_LATE_LOCK":
        queue_delay = (concurrency * 0.12) if is_hot_contention else (concurrency * 0.02)
        latency = base_latency_ms + queue_delay + random.expovariate(1.0 / (1.5 + queue_delay * 0.2))
        abort_prob = 0.018 if concurrency == 64 else (0.064 if concurrency >= 128 else 0.005)
        aborted = random.random() < abort_prob
    elif strategy == "S2_STRIPED":
        stripe_contention = max(1.0, concurrency / 16.0)
        queue_delay = (stripe_contention * 0.08) if is_hot_contention else 0.01
        latency = base_latency_ms + queue_delay + random.expovariate(1.0 / 2.0)
    elif strategy == "S4_LEDGER_ONLY":
        queue_delay = concurrency * 0.015
        latency = base_latency_ms + queue_delay + random.expovariate(1.0 / 1.8)
```
The script computed synthetic latencies and throughput mathematically using Python's `random.expovariate` and arbitrary queue delay multipliers.

---

### 2. What received the requests?
**Finding:** No service received HTTP requests. No Spring Boot JVM process and no PostgreSQL database were active during the benchmark execution. All 240 result JSON files were generated purely by in-memory mathematical simulation inside Python.

---

### 3. Does any benchmark code use `time.sleep`, `random` delays, simulated service, or in-memory model of locking strategies?
**Finding:** **YES.** The entire runner `perf/run-benchmark.py` was built around `simulate_strategy_latency()`, which used:
- `random.uniform(0.1, 0.8)`
- `random.expovariate(...)`
- `random.random() < abort_prob`
- Synthetic queue formulas modeling database row locking in-process.

---

### 4. Where do the strategies S0, S1, S2, and S4 live?
**Finding:**
- The real production strategy implementations exist in the Java ledger service:
  - `S0PessimisticStrategy.java`
  - `S1LateLockStrategy.java`
  - `S2StripedStrategy.java`
  - `S4LedgerOnlyStrategy.java`
  Configured at startup via `paycore.ledger.strategy`.
- However, the benchmark runner in `perf/run-benchmark.py` ignored these Java classes and modeled them as four branches of an `if/elif` statement in Python.

---

### 5. Does each raw run JSON contain real system telemetry?
**Finding:** No. While the JSON files recorded CPU model and memory from `/proc/cpuinfo`, the latency distributions, throughput rates (e.g. 3,906 req/s), and error counts were derived entirely from the synthetic mathematical formula.

---

### 6. Why do both `benchmarks/` and `perf/` exist?
**Finding:**
- `benchmarks/k6/` contained partial k6 JavaScript test templates (`workload-w1.js`, `workload-w2.js`, `workload-w3.js`), but was not wired into an end-to-end automated orchestrator.
- `perf/` contained the synthetic Python runner and the 240 generated JSON files.
- **Action:** Consolidated all benchmark assets into the canonical `perf/` directory as specified by the Playbook.

---

## 2. Verdict and Immediate Remediation Actions

**Provenance Verdict:** **REJECTED (SYNTHETIC / FABRICATED NUMBERS).**
Every benchmark number in the repository (including 3,906 req/s, 3,731 req/s, 2,130 req/s, 990 req/s) was produced by Python mathematical simulation and must be retracted immediately.

### Actions Taken:
1. **Deleted `perf/results/*.json`** (all 240 synthetic result files).
2. **Removed synthetic benchmark tables and throughput claims** from:
   - `README.md`
   - `docs/hot-account-report.md`
   - `docs/benchmark-report.md`
   - `web/ops-console/src/pages/SystemPage.tsx`
3. **Consolidated directories:** Moved k6 scripts from `benchmarks/k6/` into `perf/k6/` and removed the redundant `benchmarks/` directory.
4. **Prepared Playbook Section 9.3 Benchmark Protocol:**
   - Real k6 execution against the running Spring Boot ledger on `http://localhost:8082`.
   - Real PostgreSQL 16 database.
   - Seeded with 10,000 sellers before each workload.
   - Workloads W1, W2, and W3.
   - Concurrencies 1, 8, 32, 64, 128.
   - Automatic call to `/v1/integrity` after each run.
