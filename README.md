# PayCore — Payments & Settlement Platform

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://adoptium.net)
[![Spring Boot 3.3.6](https://img.shields.io/badge/Spring%20Boot-3.3.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org)
[![React 19](https://img.shields.io/badge/React-19%20%2B%20Vite-blueviolet.svg)](https://react.dev)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**PayCore** is an audit-grade, high-throughput financial ledger, automated multi-stage settlement, and reconciliation engine built for multi-sided marketplace platforms. It features strict double-entry accounting guarantees (Invariants I1–I8), four hot-account concurrency strategies sustaining up to **3,980 TPS**, automated bank/provider clearing with dynamic programming subset-sum solving, and dual-control maker-checker dispute resolution.

---

## Architecture Overview

```mermaid
flowchart TD
    Client["Client / Mobile / POS"] -->|HTTPS / REST| Ingress["Nginx Gateway :80"]
    Ingress -->|/auth/**| Identity["Identity Service :8081\n(RS256 JWKS, Token Rotation)"]
    Ingress -->|/v1/ledger/**| Ledger["Ledger Service :8082\n(Double-Entry Engine, I1-I8)"]
    Ingress -->|/v1/settlement/**| Settlement["Settlement Service :8083\n(Cutoff, 4-Stage Recon)"]
    Ingress -->|UI| Console["Ops Console :3000\n(React 19 + Vite)"]

    Ledger -->|Transactional Outbox| Kafka["Kafka KRaft Cluster\n(ledger.events)"]
    Kafka -->|CDC Event Stream| Settlement

    Identity --> DB_Id[("PostgreSQL\nidentity_db")]
    Ledger --> DB_Led[("PostgreSQL\nledger_db")]
    Settlement --> DB_Set[("PostgreSQL\nsettlement_db")]

    Ledger --> Redis[("Redis 7 Cache &\nDenylist")]
```

---

## Core Financial Guarantees (Invariants I1 – I8)

PayCore provides mathematical and architectural invariants verified by property-based testing (1,500 jqwik checks) and PostgreSQL database triggers:

| Invariant | Guarantee | Enforcement Mechanism |
| :--- | :--- | :--- |
| **I1: Double-Entry Zero Sum** | $\sum \text{Debits} = \sum \text{Credits}$ across all journal legs. | Validated in domain value object and application layer before commit. |
| **I2: Immutability** | Ledger entries cannot be modified or deleted. | PostgreSQL trigger `trg_ledger_entry_immutability` blocks `UPDATE` and `DELETE`. |
| **I3: Currency Homogeneity** | All amounts in minor units (long paise, strictly INR). | Lossless `Money` value object; floating-point types prohibited. |
| **I4: Positive Journal Legs** | Every transaction leg amount is strictly $> 0$. | Validated in `JournalLeg` model and DB check constraints. |
| **I5: Non-Negative Liability** | Merchant available balances never drop below zero. | Atomic row locking `SELECT FOR UPDATE` and check constraint. |
| **I6: Idempotent Posting** | Duplicate client requests return cached identical response. | SHA-256 payload hash locking in `idempotency_keys` table. |
| **I7: Deterministic Lock Order** | Non-hot UUIDs asc first, hot UUIDs asc second. | Global sort eliminates deadlocks under extreme concurrency. |
| **I8: Monotonic Sequence** | Entries indexed by strictly incrementing sequence. | PostgreSQL `BIGSERIAL` sequence ordering. |

---

## Hot-Account Concurrency Study Summary

When high-volume marketplaces process transactions, hot settlement accounts (such as `USER_FUNDS_IN_TRANSIT` and large enterprise sellers) suffer severe row lock contention. PayCore implements and benchmarks four concurrency strategies:

| Strategy | Description | Pareto 80/20 (W2) | Extreme Hot-Spot 95% (W3) | Abort Rate |
| :--- | :--- | :--- | :--- | :--- |
| **S0: Pessimistic Lock** | Full-transaction row lock via ascending UUID order. | 1,020 TPS | 480 TPS | 0.0% |
| **S1: Late-Lock** | Row lock deferred to the sub-millisecond commit window. | 1,450 TPS | 710 TPS | 1.8% |
| **S2: Striped Accounts** | Account balance partitioned across $N=10$ random stripes. | 2,150 TPS | 2,890 TPS | 0.0% |
| **S4: Ledger-Only** | Append-only event sourcing with async snapshots. | 3,420 TPS | **3,980 TPS** | 0.0% |

*Detailed experimental analysis, latency percentiles (p50, p95, p99), and recommended tiered topology are published in [docs/hot-account-report.md](docs/hot-account-report.md).*

---

## 100% Crash Recovery Verification (50-Iteration SIGKILL Harness)

To guarantee ACID durability against sudden catastrophic host power loss or OS panics, PayCore includes an automated 50-iteration crash harness (`tests/crash-harness/sigkill-crash-test.sh`):

```bash
# Results over 50 ungraceful SIGKILL cycles during live multi-leg transactions:
Cycle 50/50: PASSED (Orphaned entries: 0, Unbalanced journals: 0, Incomplete legs: 0)
================================================================================
Crash Verification Summary:
Total cycles executed: 50
Total cycles passed:   50 (100.0%)
Data integrity violations: 0
================================================================================
```

---

## Multi-Stage Reconciliation Engine

PayCore includes an automated 4-stage reconciliation engine capable of resolving line-level and composite bank deposits against internal ledger batches:

1. **Stage 1 (Exact Line Match):** Chunked join on `provider_txn_id` and gross amounts. Detects exact fee variances based on configured contract basis points.
2. **Stage 2 (Fuzzy Fallback Match):** Matching on order reference ID (95% confidence) and strictly unique $\pm 10$ minute timestamp windows (85% confidence).
3. **Stage 3 (Batch to Deposit Clearing via Subset Sum):** Resolves lump-sum bank deposits matching composite settlement batches using a bounded meet-in-the-middle / dynamic programming solver ($N \le 40$ batches, 5-second timeout, 100,000 state cap). Ambiguous multi-subset collisions are flagged for human operator review.
4. **Stage 4 (Ledger Clearing):** Atomic state transitions transitioning candidate records, batches, and bank deposits to `RECONCILED`.

---

## Discrepancy Cases & Dual-Control Maker-Checker

Discrepancies detected during reconciliation automatically spawn `DiscrepancyCase` entities governed by strict SLA deadlines:
- **Automated SLA Escalation Daemon:** Minute-interval background worker detecting breached deadlines, escalating severity (`LOW` $\to$ `MEDIUM` $\to$ `HIGH` $\to$ `CRITICAL`), extending deadlines, and emitting timeline audit notes.
- **Dual-Control Maker-Checker:** Operational and dispute financial adjustments require two distinct operators. An `ANALYST` proposes an adjustment, and an independent `ADMIN` must approve it. Self-approval is rejected by application logic and blocked at the PostgreSQL schema layer (`ck_cases_maker_checker`).

---

## Operations Console (React 19)

The operations console provides web-based management with enterprise security:
- **Zero LocalStorage Tokens:** Access tokens are stored strictly in-memory. Refresh tokens are exchanged via secure `httpOnly`, `SameSite=Strict` cookies with a single-flight mutex lock preventing refresh race conditions.
- **Reconciliation Workbench:** Streaming CSV file upload with SHA-256 fingerprinting, trailer validation preview, and ground-truth precision/recall metrics.
- **Discrepancy Cases:** Interactive Kanban board (`OPEN`, `IN_REVIEW`, `ESCALATED`, `RESOLVED`) and table list view with optimistic status transitions and HTTP 409 conflict detection.
- **Ledger Statement Explorer:** Keyset-paginated double-entry statement browser with available, locked, and total balance breakdowns.
- **Merchant Self-Service Portal:** Merchant portal (`/me`) displaying real-time available balance, active authorization holds, and transaction history.
- **Immutable Audit Trail:** Searchable security and financial audit log with expandable JSON event payloads.

---

## Quick Start & Verification

### Prerequisites
- Java 21 LTS (`java -version`)
- Maven 3.9+ (`mvn -version`)
- Docker & Docker Compose (`docker compose version`)
- Node.js 22+ (`node -v`)

### 1. Start Infrastructure
```bash
docker compose --profile core up -d
```
Starts PostgreSQL 16 (with isolated databases: `identity`, `ledger`, `settlement`), Redis 7, Kafka KRaft, and Nginx reverse proxy.

### 2. Build & Verify Entire Reactor
```bash
./mvnw clean verify
```
Compiles all 5 modules, executes all 77 automated test suites, validates Spotless Google Java formatting, and runs property-based tests.

### 3. Build Frontend Ops Console
```bash
cd web/ops-console
npm install
npm run build
```

### 4. Run Operational SOPs
Review standard operating procedures in [docs/runbook.md](docs/runbook.md) for daily cutoff execution, file ingestion recovery, crash recovery auditing, and invariant checks.

---

## Project Structure

```
paycore/
├── libs/
│   └── paycore-common/         # Shared Money value object, RequestIdFilter, Security filters
├── services/
│   ├── identity-service/       # RS256 JWKS publisher, refresh rotation, user admin
│   ├── ledger-service/         # Double-entry posting engine, hot-account strategies, holds/payouts
│   └── settlement-service/     # Cutoff batching, streaming file parser, recon engine, maker-checker
├── web/
│   └── ops-console/            # React 19 + TypeScript + Tailwind Ops Console
├── benchmarks/
│   ├── k6/                     # Parameterized load tests (W1 uniform, W2 Pareto, W3 hot-spot)
│   └── runner.sh               # Automated benchmark execution script
├── tests/
│   └── crash-harness/          # 50-cycle SIGKILL crash test harness
├── deploy/                     # Docker compose, PostgreSQL init scripts, Nginx gateway
└── docs/
    ├── runbook.md              # Operational SOPs and SQL invariant audit scripts
    ├── hot-account-report.md   # Hot-account concurrency benchmark findings
    └── adr/                    # Architectural Decision Records (ADR-001 to ADR-005)
```

---

## Third-Party Notices & Attribution

PayCore builds on patterns and infrastructure from open-source MIT-licensed repositories:
- **`Dedmoo/RetailBankingCore`** (commit `955c2f7`): Outbox pattern messaging, Redis token bucket rate limiting patterns, and Testcontainers baseline configuration.
- **`Gabrielab5/IssuesFlow`** (commit `ef4bd84`): Frontend layout architecture, scheduled daemon pattern, and JWT token deny-list structure.

Complete licensing details and attribution notices are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
