# ADR 009: Idempotency Transaction Boundaries, Crash Windows, and Database Backstop

## Status
Accepted

## Context
A review of the idempotency framework identified a critical atomicity question (G2 / WP-GATE-2C):
If claiming the idempotency key, posting double-entry journals, writing outbox events, and marking the key as `COMPLETED` occur across separate database transactions, crash windows exist where a process crash (e.g. `SIGKILL`) can lead to:
1. A journal committing in PostgreSQL while the idempotency key remains `IN_FLIGHT`.
2. A subsequent startup sweep or client replay marking the key `FAILED` and re-posting, causing a duplicate journal (violating Invariants I6 and I8).
3. In Spring Boot, calling a `@Transactional(REQUIRED)` method from within the same class bypasses the Spring AOP proxy, executing in autocommit mode rather than joining the caller's transaction.
4. An arbitrary 5-second startup sweep can prematurely mark active requests failed during rolling restarts in a multi-instance cluster.

## Decision

### 1. Atomic Transaction Boundaries via Spring Proxy
Posting requests follow strict transactional phasing:
1. **Key Claim (Phase 1):** `claimKey(scope, key, requestHash)` executes in a short independent transaction (`REQUIRES_NEW`). It claims `status = IN_FLIGHT`. If a concurrent request arrives with the exact same `(scope, key)`, it detects the in-flight record and immediately returns HTTP 409 `request_in_progress`.
2. **Posting & Completion (Phase 2):** Inside the transactional service method (`PaymentService.doCapture`, `RefundService.doRefund`, `PayoutService.doInitiate`), the business logic, journal persistence, outbox event generation, and idempotency status update to `COMPLETED` execute within the **same atomic database transaction** (`Propagation.REQUIRED`):
   - Double-entry validation (Invariant I1).
   - Account locks in deterministic order.
   - Journal header and ledger entry insertion (Invariants I2, I4).
   - Transactional outbox event insertion.
   - Calling `idempotencyService.completeKey(scope, key, responseJson, statusCode)`: because this call is made from the service bean into the injected `IdempotencyService` bean, it travels through the Spring AOP proxy and joins the active transaction.
   - In addition, `IdempotencyService` injects `@Lazy private IdempotencyService self` so that any internal invocations also traverse the Spring AOP proxy.
   - **PostgreSQL Commit:** All mutations commit atomically in a single disk flush. If an exception occurs at any point before commit, all journal rows, outbox rows, and idempotency completion records roll back together.

### 2. Caller Scoping on Journals & Idempotency Records
- `IdempotencyService.resolveCurrentScope()` extracts the authenticated principal name from `SecurityContextHolder`.
- Both `IdempotencyRecord` and `JournalRequest` receive this scope directly.
- Flyway migration `V4__journal_idempotency_scope_backstop.sql` establishes a compound unique constraint:
  `UNIQUE (scope, idempotency_key)` on the `journals` table.
- Different callers (e.g. `partner_a` and `partner_b`) can use the same idempotency key value without false collisions, while the same caller cannot create two journals with one key.

### 3. Response Status Code and Replay Semantics
- Flyway migration `V5__idempotency_status_code.sql` adds column `response_status_code INTEGER DEFAULT 201` to `idempotency_keys`.
- When a request is replayed from cache:
  - The stored HTTP response code (e.g. 201 Created) and JSON body are returned.
  - The response header `Idempotent-Replayed: true` is attached.
  - No new database rows are created.

### 4. Single Consistent Crash Recovery Policy (60-Second Stale Takeover)
- The premature 5-second startup sweep is removed.
- Crash recovery is unified under the 60-second stale in-flight takeover policy:
  - If a service instance crashes while a request is in Phase 2, the uncommitted transaction rolls back in PostgreSQL. The key remains `IN_FLIGHT`.
  - While within 60 seconds of initial creation, any immediate client retry returns HTTP 409 `request_in_progress`, preventing duplicate concurrent execution.
  - Once 60 seconds have elapsed, any subsequent retry takes over the stale key (`takeOverStaleKey`) and safely executes Phase 2.
  - If the request had already committed before the crash, the key is already `COMPLETED` in the database. Any post-restart retry immediately returns the stored response snapshot with `Idempotent-Replayed: true`.

---

## Consequences
- Atomicity is guaranteed: journals, outbox events, and completed idempotency records always commit or roll back together.
- Calling `completeKey` through the Spring bean proxy ensures propagation is strictly respected.
- Replaying a 201 Created request returns 201 Created with `Idempotent-Replayed: true`.
- Zero duplicate journals across all failure modes and crash windows.
