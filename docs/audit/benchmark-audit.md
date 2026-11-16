# Forensic Audit of Benchmark Suite & Protocol (Hypotheses G3 and G5)

## 1. Executive Summary & Verdict
- **Verdict on Hypothesis G3 (File Count):** **RESOLVED WITH MATHEMATICAL EVIDENCE**
  - The 240 JSON files in `perf/results/` adhere strictly to the benchmark protocol: 180 raw execution runs ($4 \text{ strategies} \times 3 \text{ workloads} \times 5 \text{ concurrency levels} \times 3 \text{ repeats}$) plus 60 median summary files ($4 \times 3 \times 5 = 60$). Total = 240 files.
- **Verdict on Hypothesis G5 (Stale Numbers & Marketing Adjectives):** **CONFIRMED & REMEDIATED**
  - Stale numbers (e.g. hand-typed 3,420 TPS, 2,890 TPS, 1,020 TPS, 3,980 TPS) and speculative terms ("hybrid tiered", "dynamic switching", "mega-merchant", "audit-grade", "enterprise") were identified across `README.md`, `docs/hot-account-report.md`, `agent_report.md`, and `SystemPage.tsx`.
  - All occurrences have been removed or replaced with values directly traceable to `perf/results/*.json` and `docs/benchmark-report.md`.

---

## 2. Arithmetic Reconciliation of 240 Result Files (G3)

The benchmark matrix defined in Playbook Section 9.2 specifies:
- **Posting Strategies (4):** `S0_PESSIMISTIC`, `S1_LATE_LOCK`, `S2_STRIPED`, `S4_LEDGER_ONLY`
- **Workloads (3):**
  - `W1`: Uniform payment captures (100% captures)
  - `W2`: Mixed marketplace flow (70% captures, 20% refunds, 10% payout requests)
  - `W3`: Pareto distribution (80% of captures directed to 20% of sellers)
- **Concurrency Levels (5):** 1, 8, 32, 64, 128 virtual users / concurrent threads
- **Repeat Count:** 3 runs per cell ($N_{repeats} = 3$)

$$\text{Test Cells} = 4 \times 3 \times 5 = 60 \text{ parameter cells}$$
$$\text{Raw Execution Runs} = 60 \times 3 = 180 \text{ files (named } \texttt{bench\_<W>\_<S>\_<C>\_run<R>.json}\text{)}$$
$$\text{Median Summary Outputs} = 60 \times 1 = 60 \text{ files (named } \texttt{bench\_<W>\_<S>\_<C>\_median.json}\text{)}$$
$$\mathbf{\text{Total Generated JSON Files}} = 180 + 60 = \mathbf{240 \text{ files}}$$

Every file in `perf/results/` corresponds directly to this matrix. There are zero unexplained files, zero extra workloads, and zero untracked warm-up files.

---

## 3. Decision-Rule Verification (Playbook Section 9.3)

**Decision Rule:** The production default strategy is the strategy with the highest sustained throughput at p99 latency under 250 ms and zero invariant violations across all concurrency levels.

| Strategy | W2 Peak Throughput | W2 p99 Latency (c=32) | W2 p99 Latency (c=128) | Abort Rate (c=128) | Decision Rule Verdict |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `S0_PESSIMISTIC` | 990.5 req/s | 45.54 ms | 150.16 ms | 0.0% | Fails throughput ceiling (collapses to 223.7 req/s at c=128) |
| `S1_LATE_LOCK` | 2,129.9 req/s | 14.61 ms | 37.80 ms | 5.51% | Fails zero-abort criterion (serialization conflicts at high concurrency) |
| **`S2_STRIPED`** | **3,905.6 req/s** | **11.33 ms** | **11.61 ms** | **0.0%** | **SELECTED:** Highest throughput, p99 < 12 ms, 0.0% aborts, live balance |
| `S4_LEDGER_ONLY` | 3,731.3 req/s | 10.79 ms | 12.23 ms | 0.0% | Viable append-only fallback; requires asynchronous balance reconstruction |

**Selection:** `S2_STRIPED` is confirmed as the production default strategy (documented in ADR 008).

---

## 4. Stale Phrase & Number Remediation Log (G5)

| File | Prior Stale Content | Corrected Content | Reason |
| :--- | :--- | :--- | :--- |
| `README.md` | "audit-grade, high-throughput" | Removed marketing adjectives | Non-negotiable rule 10 |
| `README.md` | "sustaining up to 3,980 TPS" | Updated to measured `3,906 req/s` (W2) / `3,816 req/s` (W1) | Traced to `docs/benchmark-report.md` |
| `README.md` | Table quoting 1,020 / 2,150 / 2,890 / 3,420 / 3,980 | Replaced with measured median throughput table from `perf/results/` | Traced to generated JSONs |
| `docs/runbook.md` | "SOP-05: Dynamic Switching" | "SOP-05: Hot-Account Strategy Configuration (Requires Service Restart)" | Confirmed static configuration per ADR 008 |
| `web/ops-console/src/pages/SystemPage.tsx` | Quoted 1,240 / 2,890 / 3,980 TPS with N=10 | Updated to measured values with N=16 stripes | Code and UI alignment with benchmark report |
| `benchmarks/k6/workload-w3.js` | "95% of traffic hits 1 mega-merchant" | "80% of traffic hits top 20% of sellers (Pareto W3)" | Aligned with Playbook Section 9.2 spec |

---

## 5. Experimental Conditions & Honesty Caveats
- All benchmark measurements were obtained on a single standalone host (Linux 7.2.15-omarchy-x86_64, 12 cores, 32 GB RAM).
- Workloads were executed with background user applications closed.
- Data sets are synthetic, generated strictly by `perf/workloads/` scripts.
- Single-node measurements should not be extrapolated to multi-region distributed networks.
