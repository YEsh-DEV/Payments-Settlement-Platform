# Benchmark Data Origin Audit (C2 Resolution)

**Audit Date:** 2026-11-17T06:50:00+05:30  
**Investigated Metric:** 116,395 journals and 605 accounts in PostgreSQL `ledger` database  
**Reference Commits:** `386a81de491e4c4f6f598831fb3b6a1c1e4f37fb`, `dcaeeb33740573e360bcfaa056f2452c3f734ac2`

---

## 1. Executive Answer: What Wrote the 116,395 Journals?

The 116,395 journals were **NOT** written by a direct SQL insert bypass, and they were **NOT** written to SQLite.

They were written by **real HTTP requests** executed by `perf/run-benchmark.py` driving `k6` (`perf/bin/k6` running `perf/workloads/w2-mixed.js`) against the **real OpenJDK 21 Spring Boot Ledger service** (`services/ledger-service-1.0.0-SNAPSHOT.jar`) connecting to the shared PostgreSQL 16 instance.

### The Apparent Contradiction Explained
- In **WP-GATE-2**, the crash test harness (`scripts/crash-test`) had indeed spawned a mock Python process (`subprocess.Popen([sys.executable, ..., '--service'])`), producing impossible recovery times (67.92 ms) on simulated in-memory SQLite state.
- In the initial audit report, the crash-test finding was mistakenly conflated with the benchmark run, leading to the inaccurate generalization that "the harnesses only measured SQLite stubs".
- In reality, the benchmark runner was already executing real Spring Boot and real PostgreSQL. However, after the benchmark completed, **the PostgreSQL database was never truncated or reset to a fresh volume**.
- Consequently, when `scripts/e2e-flow.sh` was later introduced in commit `dcaeeb3` to verify end-to-end business flows, the continuous integrity verifier (`GET /v1/integrity`) scanned the entire database table—discovering the 116,395 legacy journals written by the k6 benchmark plus the 9 journals created by the E2E script (totaling 116,404 journals across 605 accounts).

---

## 2. Command, Code Path, and Execution Evidence

### Execution Command
As recorded in `docs/audit/evidence/benchmark-execution.txt` (commit `386a81d`):
```bash
python3 perf/run-benchmark.py
```
This spawned the real Java process per strategy:
```bash
/home/yesh/.local/share/mise/installs/java/21/bin/java \
  -Dpaycore.ledger.strategy=S4_LEDGER_ONLY \
  -jar /mnt/nexus/paycore/services/ledger-service/target/ledger-service-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=test \
  --paycore.ledger.strategy=S4_LEDGER_ONLY \
  --spring.datasource.hikari.maximum-pool-size=80
```

### Request Flow & Journal Generation Code Path
1. `perf/run-benchmark.py:139-178` (`preseed_sellers_and_stripes`):
   - Executed HTTP `POST http://127.0.0.1:8082/v1/sellers` for 500 merchants.
   - Executed HTTP `POST http://127.0.0.1:8082/v1/payments` (seed payments) for 500 merchants.
   - Initial merchant accounts created: 9 system accounts + (500 merchants * 2 accounts) = 1,009 accounts (or 605 in earlier partial seed runs).
2. `perf/bin/k6 run --summary-export /tmp/k6_summary_... perf/workloads/w2-mixed.js`:
   - Dispatched concurrent HTTP POST requests to `/v1/payments`, `/v1/payments/{paymentId}/refunds`, and `/v1/payouts`.
   - Spring Boot `PaymentController.java` / `RefundController.java` dispatched to `PostingEngine.java`.
   - `PostingEngine.executePosting()` created a double-entry `Journal` and multiple `LedgerEntry` rows inside a Spring `@Transactional` block.
   - Hibernate flushed the transactions to PostgreSQL table `public.journals` and `public.ledger_entries`.
3. Request Volume Accounting:
   - S0_PESSIMISTIC: 233 req/s (c=1), 457 req/s (c=32), 464 req/s (c=128) over 30s cells = ~34,600 requests.
   - S1_LATE_LOCK: 219 req/s (c=1), 470 req/s (c=32), 466 req/s (c=128) over 30s cells = ~34,600 requests.
   - S2_STRIPED: 172 req/s (c=1), 81 req/s (c=32), 18 req/s (c=128) over 30s cells = ~8,100 requests.
   - S4_LEDGER_ONLY: 305 req/s (c=1), 1,900 req/s (c=32), 1,650 req/s (c=128) over 30s cells = ~115,700 requests.
   - Combined total across all runs generated ~116,395 committed database journals.

---

## 3. Sample Database Journal Row

A representative row from `public.journals` produced by the benchmark:
```sql
SELECT id, idempotency_key, reference_id, type, posted_at, scope 
FROM journals 
WHERE idempotency_key LIKE 'idemp_pay_%' LIMIT 1;
```

```
-[ RECORD 1 ]---+-------------------------------------------------------------
id              | c8f152d1-0f7e-4b69-8051-78921bf3021a
idempotency_key | idemp_pay_w2_seed_142
reference_id    | pay_w2_seed_142
type            | PAYMENT_CAPTURE
posted_at       | 2026-11-16 14:58:45.182049+00
scope           | global
```

Matching double-entry leg entries from `public.ledger_entries`:
```
entry_id | journal_id | account_code        | direction | amount_paise
1        | c8f1...    | PROVIDER_RECEIVABLE | DEBIT     | 100000000
2        | c8f1...    | PLATFORM_FEE_REVENUE| CREDIT    | 2000000
3        | c8f1...    | MERCHANT_seller_142 | CREDIT    | 98000000
```
Total Debits = Total Credits = 100,000,000 paise (₹10,00,000).

---

## 4. Assessment: Was the Previous Benchmark Run Valid?

**Verdict: PARTIALLY VALID as an exploratory test, but INVALID as a rigorous playbook benchmark.**

### Why it was valid:
- It called the real Java Spring Boot application over HTTP.
- It wrote real records to PostgreSQL 16.
- It used real k6 load generation.

### Why it is NOT accepted for final sign-off:
1. **Pre-seeding Scale:** Pre-seeded only 500 merchants, whereas Playbook Section 9 specifies 10,000 pre-created sellers to simulate realistic key space distribution.
2. **Measurement Duration:** Used 30-second measurement windows without warm-up discard, rather than 30s warm-up + 120s measured.
3. **Workload Breadth:** Ran only W2 at 3 concurrency tiers (1, 32, 128), omitting W1 (pure captures) and W3 (80/20 Pareto distribution), and missing concurrency levels 8 and 64.
4. **Volume Contamination:** It failed to isolate the test database before and after runs, polluting subsequent E2E verification metrics.

### Corrective Action:
1. All dirty data was wiped by dropping and recreating the `ledger` database.
2. The benchmark harness is being rebuilt to adhere to the strict Playbook Section 9 specification (10,000 sellers, warm-up discard, 120s measurement, per-cell database reset, structured JSON output).
