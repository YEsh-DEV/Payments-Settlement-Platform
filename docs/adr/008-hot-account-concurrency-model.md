# ADR-008: Hot-Account Concurrency Model, Strategy Selection, and Benchmark Protocol

## Status
Accepted

## Context
Marketplace platforms process transactions where every order touches common platform accounts (such as `PLATFORM_FEE_REVENUE` and `PROVIDER_RECEIVABLE`), while crediting distinct merchants. The audit of earlier reports revealed several critical gaps:
1. **Hot-Account Misidentification (F7):** The report erroneously characterized merchants as hot accounts and referenced an unaligned account `USER_FUNDS_IN_TRANSIT`. In reality, only system platform accounts (`PLATFORM_FEE_REVENUE`, `PROVIDER_RECEIVABLE`) are hot, appearing in every payment capture. Seller accounts are non-hot and strictly use pessimistic row locking.
2. **Strategy Selection Mechanism (F8):** Reports erroneously claimed dynamic runtime strategy switching. In production double-entry ledgers, dynamic switching mid-flight introduces balance inconsistency between stripes, snapshots, and master rows. The Playbook Section 9.1 dictates that strategy selection is configured statically via `paycore.ledger.strategy` (requiring service restart).
3. **Unexplained Abort Rates (F9):** S1 (Late Lock) exhibited aborts under high concurrency that were previously unexplained. Under high concurrency (64+ concurrent connections), multiple transactions defer hot-account row locking to commit time; simultaneous attempts to acquire the row lock can trigger database lock wait timeouts or serialization deadlocks. S0 queues on pessimistic locks at start (0% aborts, higher latency). S2 spreads lock contention over $N = 16$ stripes (0% aborts, high throughput). S4 avoids locking the hot row entirely (0% aborts, append-only).
4. **Stripe Factor Alignment (F10):** S2 stripe count is standardized to $N = 16$.
5. **Benchmark Protocol Gaps (F5, F6):** Earlier benchmark numbers were analytical estimates lacking raw JSON files in `perf/results/`, repeat runs, and post-run integrity verification.

## Decision
1. **Hot-Account Definition:**
   - Only platform accounts `PLATFORM_FEE_REVENUE` and `PROVIDER_RECEIVABLE` have `is_hot = true`.
   - All merchant accounts (`SELLER_PAYABLE`, `SELLER_HELD`) and bank accounts have `is_hot = false` and always use pessimistic locking.

2. **Static Strategy Configuration:**
   - Active strategy is set via configuration property `paycore.ledger.strategy` (`S0_PESSIMISTIC`, `S1_LATE_LOCK`, `S2_STRIPED`, or `S4_LEDGER_ONLY`). Service restart is required to switch strategies.

3. **Standardized Stripe Factor:**
   - Strategy S2 uses $N = 16$ stripes per hot account. The stripe index is chosen via `Math.abs(hash(journalId)) % 16`. Live balance is calculated via `SELECT SUM(balance_paise) FROM account_stripes WHERE account_id = :id`.

4. **Rigorous Benchmark Protocol (Playbook Section 9.2):**
   - **Workload W1 (Uniform Captures):** 100% payment captures distributed across merchant accounts.
   - **Workload W2 (Mixed Marketplace Flow):** 70% payment captures, 20% refunds, 10% payout requests.
   - **Workload W3 (Pareto Hot-Spot):** 80% of payment captures concentrated on 20% of merchants.
   - **Concurrency Levels:** 1, 8, 32, 64, 128 virtual users / threads.
   - **Measurement:** 3 runs per cell, taking the median throughput (req/s), p50, p95, p99 latency (ms), and abort rate (%).
   - **Continuous Invariant Gate:** Every run must be followed immediately by `GET /v1/integrity`. Any violation (I1, I3, I4) invalidates the run.
   - **Decision Rule:** Select the strategy achieving the highest sustained throughput with p99 latency < 250 ms and zero integrity violations.

5. **Strategy Evaluation Summary:**
   - **S0 (Pessimistic):** Lock serialization on hot rows limits throughput at high concurrency. Recommended only for low-volume environments.
   - **S1 (Late Lock):** Reduces lock hold duration but suffers from transaction aborts when hot-account contention spikes.
   - **S2 (Striped):** Achieves high sustained throughput with zero aborts and p99 < 100 ms by distributing write contention over 16 stripes. Maintains live queryable balance via indexed stripe sum. Selected as the recommended production strategy.
   - **S4 (Ledger Only):** Highest raw write throughput by eliminating live hot-account row updates entirely. Requires asynchronous background snapshot checkpointing.

## Consequences
- Every benchmark number published in the repository is generated from raw execution files stored in `perf/results/*.json`.
- Zero ambiguity regarding which accounts are striped or locked.
- Abort metrics are fully classified into deadlocks, timeouts, and serialization failures.
