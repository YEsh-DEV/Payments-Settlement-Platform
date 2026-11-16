# Settlement Service (`settlement-service`)

## 1. Purpose & Responsibilities
`settlement-service` manages merchant settlement lifecycles, automated cutoff batching, bank payout generation and confirmation, streaming ingestion of external provider settlement files, 4-stage reconciliation, and dual-control operational adjustments.

### Key Capabilities:
- **Automated Cutoff Batching (F-S-01):** Executes scheduled settlement batches with PostgreSQL session-level advisory locks to guarantee single-runner execution.
- **Bank Payout File Generation:** Dispatches bank settlement instruction CSVs with deterministic payout IDs (`batchId:sellerId`).
- **Streaming File Parser (F-S-04):** Parses multi-gigabyte settlement files with `DigestInputStream` SHA-256 deduplication and trailer checksum verification.
- **4-Stage Reconciliation Engine (F-S-05):** Matches internal settlement candidates against external provider lines across 4 cascading tiers (Exact, Windowed, Tolerance, Split).
- **Two-Person Operational Adjustments (F-S-07):** Strictly enforces maker-checker separation of duties (`ANALYST` proposes, `ADMIN` approves) before posting corrective entries to the ledger.

---

## 2. Configuration Parameters

| Property | Default Value | Description |
|---|---|---|
| `server.port` | `8083` | Service HTTP API port |
| `management.server.port` | `9083` | Actuator and Prometheus metrics port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/settlement` | PostgreSQL connection URL |
| `paycore.settlement.ledger-url` | `http://localhost:8082` | URL of Ledger Service |
| `paycore.settlement.identity-url` | `http://localhost:8081` | URL of Identity Service |
| `paycore.settlement.client-id` | `settlement-service` | M2M client ID for ledger operations |
| `paycore.settlement.cutoff-cron` | `0 0 * * * *` | Hourly automated batch cutoff cron expression |

---

## 3. How It Works (Interview Defense)

1. **Batch Cutoff & Payout Lifecycle:**
   - Ingested settlement candidates in status `PENDING` with `occurredAt <= cutoffTime` are batched under a unique `batchId`.
   - Aggregated net balances per seller are calculated.
   - Payout requests are initiated on `ledger-service` (`POST /v1/payouts`), debiting `SELLER_PAYABLE` and crediting `PAYOUT_IN_TRANSIT`.
   - Bank payout CSV is generated (`POST /v1/batches/{batchId}/send-payout-file`).
   - Bank confirmation CSV confirms transfer, calling `ledgerClient.confirmPayout()`, debiting `PAYOUT_IN_TRANSIT` and crediting `BANK_CASH`.

2. **Reconciliation & Maker-Checker Adjustments:**
   - Provider file uploaded via `POST /v1/files/upload` is streamed and verified.
   - Reconciliation engine executes cascading matching stages.
   - Identified fee discrepancies create `DiscrepancyCase` entities.
   - An operational analyst proposes an adjustment (`POST /v1/adjustments/propose`).
   - An administrator approves the adjustment (`POST /v1/adjustments/{id}/approve`). If proposer equals approver, `MakerCheckerViolationException` is raised.
   - Upon approval, the adjustment is posted to `ledger-service`, debiting `PROVIDER_FEE_EXPENSE` and crediting `PROVIDER_RECEIVABLE`.
