# ADR 009: Idempotency Transaction Boundaries, Crash Windows, and Database Backstop

## Status
Accepted

## Context
A review of the idempotency framework identified a critical atomicity question (G2):
If claiming the idempotency key, posting double-entry journals, writing outbox events, and marking the key as `COMPLETED` occur across separate database transactions, crash windows exist where a process crash (e.g. `SIGKILL`) can lead to:
1. A journal committing in PostgreSQL while the idempotency key remains `IN_FLIGHT`.
2. A subsequent startup sweep or client replay marking the key `FAILED` and re-posting, causing a duplicate journal (violating Invariants I6 and I8).
3. Ambiguity in multi-instance environments if one node restarts while another is actively processing a request.

## Decision

### 1. Atomic Transaction Boundaries
Posting requests follow strict transactional phasing:
1. **Key Claim (Phase 1):** `claimKey(scope, key, requestHash)` executes in a short independent transaction (`REQUIRES_NEW`). It claims `status = IN_FLIGHT`. If a concurrent request arrives with the exact same `(scope, key)`, it detects the in-flight record and immediately returns HTTP 409 `request_in_progress`.
2. **Posting & Completion (Phase 2):** The business logic (`action.get()`), journal entry persistence, outbox event generation, and idempotency status update to `COMPLETED` (including response snapshot body) execute within the **same atomic database transaction** (`Propagation.REQUIRED`):
   - Double-entry validation (Invariant I1).
   - Account locks in deterministic order.
   - Journal header and ledger entry insertion (Invariants I2, I4).
   - Transactional outbox event insertion.
   - Idempotency record status transition to `COMPLETED` and response snapshot caching.
   - **PostgreSQL Commit:** All four mutations commit atomically in a single disk flush.

### 2. Physical Database Backstop (Invariant I8 & I6 Guarantee)
Even in the event of unforeseen software logic faults, the physical database schema enforces uniqueness:
- Flyway migration `V4__journal_idempotency_scope_backstop.sql` adds a `scope` column to `journals` and establishes a compound unique constraint:
  `UNIQUE (scope, idempotency_key)`
- It is physically impossible for PostgreSQL to commit two journals for the same idempotency key within the same caller scope. Any duplicate insert immediately fails with a `23505 unique_violation`.

### 3. Crash Windows and Recovery Behavior

| Crash Window | State at Crash | Post-Restart Database State | Client Replay Behavior | Guarantee |
| :--- | :--- | :--- | :--- | :--- |
| **Window 1:** After key claimed as `IN_FLIGHT`, before journal insert | `IN_FLIGHT` row committed; no journal | `idempotency_keys` has `IN_FLIGHT`; `journals` has 0 rows | Replay after 60s stale timeout (or startup sweep) re-claims key and executes posting | Exactly 1 journal created. Zero duplicates. |
| **Window 2:** After journal flushed, before transaction commit | Transaction uncommitted in PostgreSQL buffer pool | PostgreSQL terminates socket and rolls back uncommitted transaction. Zero journal rows, zero outbox events. | Key remains `IN_FLIGHT` from Phase 1. Replay re-executes Phase 2 cleanly | Exactly 1 journal created. Zero duplicates. |
| **Window 3:** After transaction commit, before HTTP response reaches client | Transaction fully committed in PostgreSQL (`journals`, `entries`, `outbox`, and `idempotency_keys=COMPLETED`) | All rows persisted. `idempotency_keys` status is `COMPLETED` with stored response snapshot. | Replay hits Phase 1 cache check, detects `COMPLETED`, returns stored response with `Idempotent-Replayed: true` | Zero duplicate journals. Response snapshot returned. |

### 4. Startup Sweep Policy
- **Single-Instance Deployment (Default in Compose/Core):** On startup, `IdempotencyService.recoverOrphanedInFlightKeysOnStartup()` sweeps `IN_FLIGHT` keys older than a safety margin (5 seconds) and transitions them to `FAILED` so clients whose connections were severed by the crash can immediately replay without waiting for the full 60-second stale timeout.
- **Safety Proof:** Because `COMPLETED` commits in the exact same transaction as the journal, an `IN_FLIGHT` key that survives a crash is mathematically guaranteed to have **zero committed journals**. Therefore, transitioning it to `FAILED` for retry is 100% safe.
- **Multi-Instance Deployment:** In multi-node deployments, node-level startup sweeps are disabled (`paycore.ledger.idempotency.startup-sweep=false`). Reclaiming orphaned keys is handled exclusively by the 60-second stale in-flight takeover mechanism (`stale in-flight takeover`).

## Consequences
- Atomicity is guaranteed: journals and completed idempotency snapshots can never desynchronize.
- The database unique constraint on `(scope, idempotency_key)` serves as an immutable physical backstop.
- Replaying any interrupted or completed request guarantees zero duplicate journals across all failure modes.
