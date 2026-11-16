# PayCore Hot-Account Strategy Study Report

Architectural analysis of four hot-account posting strategies under concurrency and account skew (Playbook Section 9).

---

## 1. Executive Summary

Central clearing accounts in payment settlement platforms (such as `USER_FUNDS_IN_TRANSIT` and `PLATFORM_FEE_REVENUE`) and high-volume merchant accounts experience high write contention. Under naive row locking (S0), serialized database transactions on hot account rows create lock queues, high latency, and throughput collapse.

This study analyzes four distinct strategies defined in Playbook Section 9.1:
- **S0 (PESSIMISTIC):** Immediate row lock via `SELECT ... FOR UPDATE` with global deadlock-free ordering.
- **S1 (LATE_LOCK):** Deferred hot-account lock acquisition until right before SQL commit.
- **S2 (STRIPED):** Multi-stripe partitioning of hot accounts ($N = 16$) with random stripe assignment.
- **S4 (LEDGER_ONLY):** Append-only event-sourced ledger entries with zero hot-row balance mutation on the write path.

The benchmark provenance investigation and retracted synthetic numbers are documented in [docs/audit/benchmark-provenance.md](audit/benchmark-provenance.md). Live empirical benchmark execution is planned under Playbook Section 9.3 protocols.

---

## 2. Benchmark Workloads and Execution Protocol

The benchmark suite defines three workloads specified in Playbook Section 9.2:
- **W1 (Uniform Captures):** 100% payment captures across distinct seller accounts.
- **W2 (Mixed Marketplace Flow):** 70% payment captures, 20% refunds, 10% payout requests.
- **W3 (Pareto Hot-Spot):** 80% of payment captures concentrated on 20% of sellers.

Each cell evaluates concurrency levels ($c \in \{1, 8, 32, 64, 128\}$) with continuous `/v1/integrity` audit calls.

---

## 3. Architectural Analysis and Trade-Offs

### 3.1 S0: Pessimistic Row Locking
- **Mechanism:** Acquires row-level exclusive locks (`FOR UPDATE`) in deterministic account ID order during the transaction.
- **Trade-off:** Strong immediate consistency, but throughput drops under high concurrency due to serialized lock queues.
- **Conclusion:** Suitable only for low-volume or evenly distributed systems; not suitable for central clearing accounts.

### 3.2 S1: Late Locking
- **Mechanism:** Executes non-hot account modifications and invariant checks first; acquires exclusive lock on the hot clearing account only immediately prior to transaction commit.
- **Trade-off:** Reduces lock hold duration. However, under extreme concurrency, conflicting transactions attempting to acquire the hot row lock cause serialization failures and transaction rollbacks.
- **Conclusion:** Effective at moderate concurrency; higher conflict rates under peak contention.

### 3.3 S2: Striped Accounts ($N = 16$)
- **Mechanism:** Divides hot clearing accounts into $N = 16$ independent balance stripe rows (`account_stripes`). Write transactions pick a pseudo-random stripe using bitwise masking (`stripe_index = thread_hash & 15`). Read balance queries compute $\sum \text{stripe\_balance}$.
- **Trade-off:** Reduces hot row lock contention by a factor of 16 while retaining immediate relational consistency.
- **Conclusion:** Recommended production posting strategy in ADR 008.

### 3.4 S4: Ledger-Only (Event-Sourced Append)
- **Mechanism:** Writes immutable journal and entry rows without updating balance rows in the critical transaction path. Account balances are derived asynchronously by background snapshot aggregators.
- **Trade-off:** High write throughput with zero row locking on balances. However, point-in-time balance lookups require reading un-snapshotted journal entries or accepting eventual consistency.
- **Conclusion:** Useful for ultra-high-throughput ingestion, but adds operational complexity for real-time payout validations that require immediate available balance verification.

---

## 4. Production Strategy Configuration

In accordance with ADR 008 and Playbook Section 9.1:
- Strategy is configured at application startup via `paycore.ledger.strategy=S2_STRIPED`.
- Stripe allocation is defined in schema migration `V2__striped_accounts.sql` with $N = 16$ stripes.
- Balance verification for striped accounts computes the aggregate balance across all 16 stripes, satisfying Invariant I4.
