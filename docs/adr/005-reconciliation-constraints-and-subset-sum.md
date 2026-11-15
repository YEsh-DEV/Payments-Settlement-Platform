# ADR-005: Reconciliation Matching Guardrails and Subset Sum Bounded Complexity

## Status
Accepted

## Context
Reconciliation Stage 3 matches payment provider settlement batches to company bank statement deposits. While single-batch deposits match 1:1, payment providers frequently combine multiple batches into a single bank deposit, occasionally minus reserve holdbacks. Identifying which combination of batches sums to a target deposit amount is an instance of the **Subset Sum Problem**, which is NP-complete. Without strict computational boundaries, unbounded search could monopolize CPU resources and induce denial-of-service conditions.

## Decision
1. **Four-Stage Phased Pipeline:**
   - **Stage 1 (Line Matching):** In-memory hash join on `provider_txn_id` chunked by `settle_date`. Identifies exact matches, gross amount mismatches, fee variances, duplicates, and missing transactions.
   - **Stage 2 (Fuzzy Fallback):** Secondary matching on `merchant_ref == orderId` or equal amount within a $\pm 10\text{-minute}$ time window and unique candidate resolution. Multiple candidates trigger an `AMBIGUOUS_MATCH` exception; guessing is strictly prohibited.
   - **Stage 3 (Batch to Deposit):**
     1. Exact 1:1 net batch matching.
     2. Provider batch IDs parsed from bank reference text hints.
     3. **Bounded Subset Search:** Dynamic programming over reachable sums with state pruning, or meet-in-the-middle binary search for up to 40 batches.
2. **Hard Computational Safeguards:**
   - **Execution Timeout:** Maximum **5.0 seconds** per bank deposit search.
   - **State Space Cap:** Maximum **50,000 states** tracked in the dynamic programming reachability table.
   - **Ambiguity Policy:** If more than one subset combination sums to the deposit amount (within reserve tolerance), the algorithm marks the deposit `DEPOSIT_AMBIGUOUS` and creates an operational Case.
3. **Stage 4 (Ledger Posting):** Automated posting to `POST /v1/provider-deposits` with deterministic idempotency key `deposit:<bankDepositId>`.

## Consequences
- Guaranteed computational bounding on NP-complete algorithms.
- Complete explainability: every match stores the matched rule, confidence score, and JSON evidence.
- Complex multi-batch deposit discrepancies are escalated to human analysts via tracked Cases rather than resolved incorrectly by automated guessing.
