# ADR-006: Invariant Definitions, Chart of Accounts, Refund Overdraft Rule, and Concurrency Striping

## Status
Accepted

## Context
During the initial truth audit (WP-A), five significant discrepancies were confirmed in the ledger domain and invariants:
1. **Invariant Taxonomy Drift (F1):** The verification report had drifted from the playbook invariant numbering (e.g. defining I3 as currency homogeneity, I4 as positive legs, I5 as non-negative liability, I8 as monotonic sequence).
2. **Merchant Liability Contradiction (F2):** The report and initial implementation incorrectly asserted that merchant liability accounts (`SELLER_PAYABLE`) could never drop below zero, throwing 422 `insufficient_funds` on refunds if a merchant had zero balance. Playbook Sections 3.1 and 3.4 explicitly dictate that `SELLER_PAYABLE` may go negative through refund or dispute journals (the merchant owes the platform); only payout requests may never overdraw available balance.
3. **Missing Integrity Checker (F3):** Playbook feature F-L-09 required `GET /v1/integrity`, a nightly scheduled job, and metric `ledger_integrity_violations_total`. It was absent from `ledger-service`.
4. **Chart of Accounts Inconsistency (F7):** The initial code initialized an undocumented account `USER_FUNDS_IN_TRANSIT` instead of the canonical `PROVIDER_RECEIVABLE` and `BANK_CASH`.
5. **Striping Factor Divergence (F10):** The report claimed $N = 10$ stripes for strategy S2 (Striped), whereas Playbook Section 9.1 specifies $N = 16$.

## Decision
1. **Standardize Invariants I1 through I8:**
   - **I1 (Double-Entry Balance):** Every journal has at least two lines, strictly positive amounts, and total debits identically equal total credits ($\sum \text{DR} = \sum \text{CR}$).
   - **I2 (Entry Immutability):** Ledger entries are immutable, enforced by database trigger `trg_ledger_entry_immutability` which rejects `UPDATE` and `DELETE`.
   - **I3 (Global Zero-Sum):** Globally across all ledger entries in the database, total debits identically equal total credits.
   - **I4 (Balance Correctness):** Each cached account balance equals the sum of its entries (for S2 Striped: sum of stripes equals sum of entries; for S4 Ledger-Only: latest snapshot plus subsequent entries delta equals sum of entries).
   - **I5 (Payout Limit & Refund Overdraft Rule):** Payout requests never exceed available `SELLER_PAYABLE` (`balance - locked_balance >= payout_amount`). `SELLER_PAYABLE` is explicitly permitted to drop below zero on `REFUND` and `ADJUSTMENT` operations (reflecting that the merchant owes the platform).
   - **I6 (Idempotency):** A request with a given idempotency key produces at most one journal.
   - **I7 (Refund Cap):** Total refunds for a payment never exceed the captured gross amount.
   - **I8 (Business Operation Linkage):** Each journal links to exactly one business operation, enforced by unique constraints on `payments.journal_id`, `refunds.journal_id`, and `payouts.journal_id` (Flyway migration `V2__invariants_and_integrity.sql`).

2. **Align Chart of Accounts (Playbook Section 3.2):**
   - System Accounts:
     - `PROVIDER_RECEIVABLE` (ASSET, normal side: DEBIT, `isHot = true`, `stripeCount = 16`)
     - `BANK_CASH` (ASSET, normal side: DEBIT, `isHot = false`, `stripeCount = 1`)
     - `PAYOUT_IN_TRANSIT` (LIABILITY, normal side: CREDIT, `isHot = false`, `stripeCount = 1`)
     - `PLATFORM_FEE_REVENUE` (REVENUE, normal side: CREDIT, `isHot = true`, `stripeCount = 16`)
     - `PROVIDER_FEE_EXPENSE` (EXPENSE, normal side: DEBIT, `isHot = false`, `stripeCount = 1`)
     - `WRITE_OFF_EXPENSE` (EXPENSE, normal side: DEBIT, `isHot = false`, `stripeCount = 1`)
   - Merchant Accounts:
     - `SELLER_PAYABLE` / `MERCHANT_{sellerId}` (LIABILITY, normal side: CREDIT, `isHot = false`, `stripeCount = 1`)
     - `SELLER_HELD` (LIABILITY, normal side: CREDIT, `isHot = false`, `stripeCount = 1`)
   - Legacy aliases (`USER_FUNDS_IN_TRANSIT`, `SETTLEMENT_PAYABLE`) are retained as fallbacks for backward compatibility.

3. **Continuous Integrity Checker (F-L-09):**
   - Implemented `IntegrityService` and `IntegrityController` (`GET /v1/integrity` and `/integrity`).
   - Verifies I3 (global zero-sum), I4 (cached balance vs entry sums across S0, S1, S2, S4), and samples I1 (balanced legs and positive amounts).
   - Increments Micrometer counter `ledger_integrity_violations_total` upon any invariant violation.
   - Runs on a scheduled cron (`@Scheduled(cron = "${paycore.ledger.integrity.cron:0 0 2 * * *}")`).

4. **S2 Stripe Factor:**
   - Standardized default stripe count to $N = 16$ across all hot accounts (`PROVIDER_RECEIVABLE`, `PLATFORM_FEE_REVENUE`).

## Consequences
- Legitimate customer refunds are never blocked by a merchant having zero or insufficient balance; merchant accounts can go negative.
- Payouts are strictly prevented from overdrawing available merchant funds.
- System accounts adhere to standard double-entry accounting taxonomy.
- Continuous automated audit provides real-time detection and metrics on ledger invariants.
