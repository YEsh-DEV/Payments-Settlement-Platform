# ADR-007: Idempotency Claim Semantics, Scoping, Crash Recovery, and Lifecycle

## Status
Accepted

## Context
Idempotency in distributed payment platforms is critical to prevent duplicate transfers, multiple order debits, or conflicting settlements. Audit finding F4 confirmed several operational and protocol gaps in the initial implementation:
1. **Error Code Misalignment:** Concurrent requests returned HTTP 409 with code `concurrent_request` rather than the Playbook Section 8.2 standard `request_in_progress`.
2. **Hash Conflict Misalignment:** Reused keys with different payloads returned `idempotency_conflict` instead of `idempotency_key_reused` (HTTP 422).
3. **Missing Replay Header:** The required HTTP response header `Idempotent-Replayed: true` was not attached when replaying cached responses.
4. **Tenant Isolation Gap:** Idempotency keys were stored globally without caller scoping, allowing one client to inadvertently collide with or block another client's key.
5. **Crash Deadlock Risk:** If a service crashed during execution between key claim and completion, the key remained stuck in `IN_FLIGHT` permanently, blocking any subsequent client retry with the same key.
6. **Unbounded Storage Growth:** No retention or purging policy existed for historical idempotency records.

## Decision
1. **Caller Scoping:**
   - Idempotency records are scoped by the caller identity extracted from the JWT token subject or client ID (`Authentication.getName()`). Unauthenticated requests default to scope `global`.
   - The primary key of `idempotency_keys` is compound: `(scope, key)`, enforced in Flyway migration `V3__idempotency_scope_and_cleanup.sql`.

2. **Explicit RFC 9457 Error Codes:**
   - Same key and different payload hash: return HTTP 422 with error code `idempotency_key_reused`.
   - Same key in-flight: return HTTP 409 with error code `request_in_progress`.

3. **Replay Header:**
   - Any execution that returns a cached response attaches the response header `Idempotent-Replayed: true` via Spring's `RequestContextHolder`.

4. **Crash Recovery & Stale In-Flight Takeover:**
   - If an idempotency record has status `IN_FLIGHT` but its `created_at` timestamp is older than **60 seconds**, the system presumes the prior worker process crashed or timed out.
   - The new request resets the timestamp, takes over the `IN_FLIGHT` state, and proceeds to execute the business action.

5. **7-Day Retention Lifecycle:**
   - A scheduled cleanup daemon (`@Scheduled(cron = "${paycore.ledger.idempotency.cleanup-cron:0 0 3 * * *}")`) purges records in states `COMPLETED` or `FAILED` that are older than **7 days**.
   - An index on `(status, created_at)` ensures constant-time cleanup execution.

## Consequences
- Guaranteed safety against duplicate postings during client retries or network partition reconciliations.
- Worker process terminations no longer leave unrecoverable orphaned idempotency locks.
- Complete caller isolation guarantees zero key collision across distinct clients.
- Database table size is strictly bounded by the 7-day retention window.
