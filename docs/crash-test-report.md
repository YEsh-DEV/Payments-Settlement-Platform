# PayCore Crash and Recovery Verification Report

**Document Status:** PENDING REAL RUN  
**Reference Specification:** `PayCore Build Playbook.md`, Section 9.4  
**Target Environment:** Real OpenJDK 21 Spring Boot JVM (`services/ledger-service`) on Docker Compose with disk-backed PostgreSQL volume  

---

## 1. Scope & Execution Protocol (Step 3 Preparation)

This report will document the real crash and recovery verification protocol once Docker access is activated:

### Suite 1: JVM OS SIGKILL Recovery (50 Iterations)
1. Spawns real OpenJDK 21 JVM (`ledger-service-1.0.0-SNAPSHOT.jar`) connecting to disk-backed PostgreSQL 16.
2. Injects concurrent HTTP workload W2 (70% captures, 20% refunds, 10% payouts) with unique idempotency keys and RS256 JWTs.
3. Issues abrupt OS kernel `SIGKILL` (`kill -9 <pid>` / `docker kill --signal=KILL`).
4. Verifies process termination exit code `-9` / `137`.
5. Restarts the real service artifact and records:
   - `restart_to_healthy_ms`: Time from restart launch until `/actuator/health` returns HTTP 200 `UP`.
   - `first_request_ms`: Time from healthy state to first successfully completed HTTP response.
   - `total_recovery_ms`: Total wall-clock outage time.
6. Replays all in-flight and completed requests with original idempotency keys.
7. Executes SQL assertions across PostgreSQL WAL:
   - Zero duplicate journal entries for the same idempotency key.
   - Zero partial journals (every journal entry has balanced credits and debits).
   - In-flight requests at crash time cleanly resolve to either single success or fail with 409/422 without double-spend.

### Suite 2: PostgreSQL 16 WAL Crash Recovery (20 Iterations)
1. Injects active concurrent ledger transactions.
2. Abruptly terminates PostgreSQL container via `SIGKILL`.
3. Restarts PostgreSQL and measures WAL replay recovery time.
4. Executes `GET /v1/integrity` to prove zero invariant violations ($I1$ through $I8$).

---

## 2. Recovery Metric Distribution

*Status: Pending real run execution following Docker access activation (Step 3).*

| Metric | Target SLA | Min | Median | p95 | Max | Status |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Restart to Healthy (`restart_to_healthy_ms`)** | < 15,000 ms | *Pending* | *Pending* | *Pending* | *Pending* | PENDING |
| **Healthy to First Request (`first_request_ms`)** | < 500 ms | *Pending* | *Pending* | *Pending* | *Pending* | PENDING |
| **Total Recovery (`total_recovery_ms`)** | < 15,500 ms | *Pending* | *Pending* | *Pending* | *Pending* | PENDING |
| **Double-Spend Count** | Exactly 0 | *Pending* | *Pending* | *Pending* | *Pending* | PENDING |
| **Invariant Violations (I1..I8)** | Exactly 0 | *Pending* | *Pending* | *Pending* | *Pending* | PENDING |
