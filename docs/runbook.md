# PayCore Operational Runbook (SOPs)

Standard Operating Procedures (SOPs) for operating, maintaining, recovering, and auditing the PayCore Payments & Settlement Platform.

---

## Table of Contents

1. [SOP-01: Daily Batch Cutoff & Settlement Execution](#sop-01-daily-batch-cutoff--settlement-execution)
2. [SOP-02: Provider Settlement File Ingestion & Recovery](#sop-02-provider-settlement-file-ingestion--recovery)
3. [SOP-03: Discrepancy Case Escalation & Maker-Checker Resolution](#sop-03-discrepancy-case-escalation--maker-checker-resolution)
4. [SOP-04: Disaster Recovery & SIGKILL Crash Recovery Verification](#sop-04-disaster-recovery--sigkill-crash-recovery-verification)
5. [SOP-05: Hot-Account Strategy Configuration](#sop-05-hot-account-strategy-configuration-requires-service-restart)
6. [SOP-06: Mathematical Invariant Auditing (Invariants I1–I8)](#sop-06-mathematical-invariant-auditing-invariants-i1i8)

---

## SOP-01: Daily Batch Cutoff & Settlement Execution

### Objective
Transition settlement candidate transactions occurred up to cutoff time into a finalized `Batch`, aggregate financial totals, and generate deterministic merchant payouts.

### Execution

#### 1. Automated Execution
- Scheduled via Spring `@Scheduled` cron configured in `application.yml`:
  ```yaml
  paycore:
    settlement:
      cutoff-cron: "0 0 * * * *" # Hourly cutoff
  ```

#### 2. Manual Execution (via Ops Console or API)
- **Via Ops Console:** Navigate to **Batches & Payouts** (`/payouts`) and click **"Execute Cutoff Run"**.
- **Via REST API:**
  ```bash
  curl -X POST http://localhost:8083/v1/batches/run \
    -H "Authorization: Bearer <ADMIN_OR_ANALYST_TOKEN>" \
    -H "Content-Type: application/json" \
    -d '{}'
  ```

### Verification Checklist
1. **Batch Status Transition:** Verify the batch has transitioned to `COMPLETED`:
   ```sql
   SELECT batch_id, status, total_count, total_gross_paise, total_fee_paise, total_net_paise, closed_at
   FROM batches
   ORDER BY created_at DESC
   LIMIT 1;
   ```
2. **Deterministic Mathematical Integrity:**
   Ensure `total_net_paise = total_gross_paise - total_fee_paise`:
   ```sql
   SELECT batch_id, total_gross_paise, total_fee_paise, total_net_paise
   FROM batches
   WHERE total_net_paise != (total_gross_paise - total_fee_paise);
   -- MUST RETURN 0 ROWS
   ```
3. **Payout Generation:**
   Verify each merchant with positive net balance has an associated payout:
   ```sql
   SELECT payout_id, seller_id, batch_id, amount_paise, status
   FROM settlement_payouts
   WHERE batch_id = '<LATEST_BATCH_ID>';
   ```

---

## SOP-02: Provider Settlement File Ingestion & Recovery

### Objective
Ingest bank/provider settlement CSV files using streaming bounded memory, verify trailer row integrity, prevent duplicate ingestion, and trigger 4-stage reconciliation.

### Execution

#### 1. File Upload via REST
```bash
curl -X POST http://localhost:8083/v1/files/upload \
  -H "Authorization: Bearer <ANALYST_TOKEN>" \
  -F "file=@/path/to/provider_settlement_20261115.csv"
```

#### 2. File Ingestion Rules & Expected Format
- Header: `provider_txn_id,merchant_ref,amount,fee,currency,txn_time,status`
- Data rows: Amounts in major or minor units (minor paise converted losslessly).
- Trailer row: `TRAILER,<count>,<total_amount>`

### Error Handling & Failure Recovery

| Error / HTTP Code | Cause | Operator Recovery Action |
| :--- | :--- | :--- |
| **HTTP 409 `file_already_ingested`** | SHA-256 fingerprint matches an existing file in `provider_settlement_files`. | Inspect `SELECT file_id, original_filename, sha256_hash FROM provider_settlement_files WHERE sha256_hash = '...';`. If provider re-sent identical file, do not re-ingest. If new file has corrupted duplicate header, request re-export from provider. |
| **HTTP 422 `trailer_count_mismatch`** | Row count does not match trailer record count. | Incomplete file transfer. Check network transfer logs, re-download file from provider SFTP, and re-upload. |
| **HTTP 422 `trailer_amount_mismatch`** | Sum of row gross amounts does not match trailer total amount. | Truncated or corrupted file. Reject file with vendor; do not proceed with reconciliation. |

---

## SOP-03: Discrepancy Case Escalation & Maker-Checker Resolution

### Objective
Investigate and resolve settlement discrepancies (fee variances, missing lines, duplicate provider lines, unmatched bank deposits) enforcing the 4-eyes principle.

### Case Types & SLAs

| Case Type | Initial Severity | SLA Target |
| :--- | :--- | :--- |
| `AMOUNT_MISMATCH` | CRITICAL | 4 hours |
| `DEPOSIT_UNMATCHED` | HIGH | 24 hours |
| `DUPLICATE_PROVIDER_LINE` | HIGH | 24 hours |
| `FEE_VARIANCE` | MEDIUM | 72 hours |
| `MISSING_IN_PROVIDER` | MEDIUM | 72 hours |
| `MISSING_IN_LEDGER` | LOW | 168 hours |

### Automated SLA Escalation Daemon
- Runs every minute:
  ```
  SlaEscalationDaemon -> checks cases WHERE status NOT IN ('RESOLVED', 'WRITTEN_OFF') AND sla_deadline < NOW()
  ```
- Automatically elevates severity:
  `LOW` $\to$ `MEDIUM` $\to$ `HIGH` $\to$ `CRITICAL`
- Extends SLA deadline by 50% and appends system audit note in `case_notes`.
- Increments Prometheus counter `paycore_cases_escalated_total`.

### Dual-Control Maker-Checker Workflow

> [!IMPORTANT]
> Proposer (`proposed_by`) and Approver (`approved_by`) **MUST NEVER BE THE SAME USER**. Self-approval is rejected by application logic (`MakerCheckerViolationException`) and blocked at the PostgreSQL schema level via check constraint `ck_cases_maker_checker`.

#### Step 1: Maker Proposes Adjustment (Analyst)
- Operator role: `ANALYST`
- Action:
  ```bash
  curl -X POST http://localhost:8083/v1/cases/{caseId}/adjustments \
    -H "Authorization: Bearer <ANALYST_TOKEN>" \
    -H "Content-Type: application/json" \
    -d '{
      "template": "FEE_VARIANCE",
      "amountPaise": 200,
      "comment": "Provider deducted 200 paise excess fee on txn_001. Approved fee schedule 1.5%."
    }'
  ```
- Adjustment status: `PROPOSED`.

#### Step 2: Checker Reviews & Approves Adjustment (Administrator)
- Operator role: `ADMIN` (must differ from proposing analyst)
- Action:
  ```bash
  curl -X POST http://localhost:8083/v1/cases/{caseId}/adjustments/{adjustmentId}/approve \
    -H "Authorization: Bearer <ADMIN_TOKEN>"
  ```
- Ledger impact: Posts double-entry journal:
  - DR: `PROVIDER_FEE_EXPENSE` (or `WRITE_OFF_EXPENSE`)
  - CR: `PROVIDER_RECEIVABLE`
- Status: Adjustment becomes `POSTED`; Case transitions to `RESOLVED` (or `WRITTEN_OFF`).

---

## SOP-04: Disaster Recovery & SIGKILL Crash Recovery Verification

### Objective
Verify that sudden process termination (`SIGKILL`, node power failure, OS kernel panic) preserves 100% database integrity without orphaned records, incomplete journals, or unbalanced legs.

### Automated Test Harness Execution
Run the automated 50-iteration crash harness:
```bash
./scripts/crash-test
```

### Manual Disaster Recovery Audit Queries
Run following any ungraceful crash or database failover:

```sql
-- 1. Check for orphaned ledger entries without journal header
SELECT le.id, le.journal_id, le.amount_paise
FROM ledger_entries le
LEFT JOIN journals j ON le.journal_id = j.id
WHERE j.id IS NULL;
-- EXPECTED: 0 rows

-- 2. Check for unbalanced journals (Invariant I1)
SELECT journal_id,
       SUM(CASE WHEN direction = 'DR' THEN amount_paise ELSE -amount_paise END) AS delta_paise
FROM ledger_entries
GROUP BY journal_id
HAVING SUM(CASE WHEN direction = 'DR' THEN amount_paise ELSE -amount_paise END) != 0;
-- EXPECTED: 0 rows

-- 3. Check for incomplete journals with fewer than 2 legs
SELECT journal_id, COUNT(*) as leg_count
FROM ledger_entries
GROUP BY journal_id
HAVING COUNT(*) < 2;
-- EXPECTED: 0 rows

-- 4. Check for in-flight idempotency locks abandoned by crashed processes
SELECT idempotency_key, status, created_at
FROM idempotency_keys
WHERE status = 'IN_FLIGHT' AND created_at < NOW() - INTERVAL '5 minutes';
-- ACTION: Can be purged or unlocked if request terminated before completion.
```

---

## SOP-05: Hot-Account Strategy Configuration (Requires Service Restart)

### Objective
Configure posting strategy depending on traffic profile and platform hot-spot contention. In accordance with ADR 008 and Playbook Section 9, strategy switching requires a service restart to ensure balance caches and stripe distributions remain completely consistent.

### Strategy Profiles

| Strategy Code | Concurrency Model | Contention Profile | Recommended Use |
| :--- | :--- | :--- | :--- |
| `S0_PESSIMISTIC` | Global ascending UUID locks | Contention on hot platform account | Uniform traffic; baseline serializability. |
| `S1_LATE_LOCK` | Commit-window lock deferral | Serialization conflicts at 64+ VUs | Moderate hot-spotting; cuts row lock hold duration. |
| `S2_STRIPED` | 16-way balance striping | Contention divided across 16 stripes | Default production strategy (zero aborts, lowest p99 latency). |
| `S4_LEDGER_ONLY` | Append-only event-sourced | Zero hot row locks | High-throughput ingestion with async snapshot aggregation. |

### Strategy Configuration
Set the active strategy in `services/ledger-service/src/main/resources/application.yml` or via container environment variable (restart required):
```bash
export PAYCORE_LEDGER_STRATEGY=S2_STRIPED
```

---

## SOP-06: Mathematical Invariant Auditing (Invariants I1–I8)

Run this SQL audit script daily or as an automated monitoring job against the `ledger` database:

```sql
-- =========================================================================
-- PayCore Invariant Audit Suite
-- =========================================================================

-- Invariant I1: Double-Entry Zero Sum
-- Total debits must equal total credits across all legs of every journal.
SELECT
  'I1_ZERO_SUM' AS invariant,
  COUNT(*) AS violations
FROM (
  SELECT journal_id
  FROM ledger_entries
  GROUP BY journal_id
  HAVING SUM(CASE WHEN direction = 'DR' THEN amount_paise ELSE -amount_paise END) != 0
) sub;

-- Invariant I2: Immutability Trigger
-- Verifies trigger trg_ledger_entry_immutability is actively enabled.
SELECT
  'I2_TRIGGER_ACTIVE' AS invariant,
  CASE WHEN COUNT(*) = 1 THEN 0 ELSE 1 END AS violations
FROM pg_trigger
WHERE tgname = 'trg_ledger_entry_immutability' AND tgenabled = 'O';

-- Invariant I3: Currency Homogeneity
-- Only INR currency permitted on accounts.
SELECT
  'I3_CURRENCY_HOMOGENEITY' AS invariant,
  COUNT(*) AS violations
FROM accounts
WHERE currency != 'INR';

-- Invariant I4: Positive Amounts
-- All journal leg amounts must be strictly positive (> 0).
SELECT
  'I4_POSITIVE_AMOUNTS' AS invariant,
  COUNT(*) AS violations
FROM ledger_entries
WHERE amount_paise <= 0;

-- Invariant I5: Non-Negative Merchant Liability
-- Merchant accounts must never have negative available balance.
SELECT
  'I5_NON_NEGATIVE_BALANCE' AS invariant,
  COUNT(*) AS violations
FROM accounts
WHERE type = 'LIABILITY' AND available_balance_paise < 0;

-- Invariant I6: Monotonic Sequence Integrity
-- Entry sequence numbers must strictly increment without negative gaps.
SELECT
  'I6_MONOTONIC_SEQUENCE' AS invariant,
  COUNT(*) AS violations
FROM (
  SELECT id, LAG(id) OVER (ORDER BY id) as prev_id
  FROM ledger_entries
) seq
WHERE prev_id IS NOT NULL AND id <= prev_id;
```
