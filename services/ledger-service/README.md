# Ledger Service (`ledger-service`)

## 1. Purpose & Responsibilities
`ledger-service` is the financial core of PayCore. It implements double-entry accounting with strict invariant guarantees, pluggable concurrency strategies for hot-account handling, authorization holds, partial refunds, provider deposits, and continuous integrity audits.

### Key Capabilities:
- **Strict Double-Entry Invariant (I1):** Every journal entry enforces $\sum \text{Debits} = \sum \text{Credits}$.
- **Pluggable Hot-Account Strategies:**
  - `S0_PESSIMISTIC`: Strict row-level lock (`SELECT ... FOR UPDATE`).
  - `S1_LATE_LOCK`: Minimized transaction lock duration.
  - `S2_STRIPED`: Multi-stripe virtual sub-accounts to eliminate lock queuing.
  - `S4_LEDGER_ONLY`: Append-only journal with asynchronous materialized view projection.
- **Continuous Integrity Auditor (F-L-09):** `GET /v1/integrity` executes a live invariant verification sweep over all accounts and journals.
- **Transactional Outbox Relay:** Atomically captures domain events in PostgreSQL within the payment transaction and dispatches them reliably.

---

## 2. Configuration Parameters

| Property | Default Value | Description |
|---|---|---|
| `server.port` | `8082` | Service HTTP API port |
| `management.server.port` | `9082` | Actuator and Prometheus metrics port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/ledger` | PostgreSQL connection URL |
| `paycore.security.jwks-uri` | `http://localhost:8081/.well-known/jwks.json` | Public key endpoint for JWT validation |
| `paycore.ledger.strategy` | `S0_PESSIMISTIC` | Default posting strategy (`S0_PESSIMISTIC`, `S1_LATE_LOCK`, `S2_STRIPED`, `S4_LEDGER_ONLY`) |
| `paycore.ledger.outbox.poll-interval-ms` | `1000` | Outbox polling frequency |

---

## 3. How It Works (Interview Defense)

1. **Payment Capture & Idempotency:**
   - Request arrives at `POST /v1/payments` with `Idempotency-Key` header.
   - Idempotency record is acquired in PostgreSQL. Replayed requests return cached responses immediately.
   - Fee is computed based on merchant fee tier (e.g. `STANDARD` = 200 bps).
   - Double-entry legs are composed:
     - Debit `PROVIDER_RECEIVABLE` (gross amount)
     - Credit `SELLER_PAYABLE` (net amount)
     - Credit `PLATFORM_FEE_REVENUE` (fee amount)
   - Posting strategy acquires account locks in deterministic order to prevent deadlocks, verifies available balance ($I2$), and commits the journal entry and outbox event atomically.

2. **Continuous Integrity Audit:**
   - Invoking `GET /v1/integrity` triggers an audit job that recalculates global debits and credits across all journals, verifies balance equation $\text{Debits} - \text{Credits} = 0$, and checks cached account balances against entry leg sums.
