# PayCore: Payments & Settlement Platform

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://adoptium.net)
[![Spring Boot 3.3.6](https://img.shields.io/badge/Spring%20Boot-3.3.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org)
[![React 19](https://img.shields.io/badge/React-19%20%2B%20Vite-blueviolet.svg)](https://react.dev)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**PayCore** is a double-entry financial ledger, automated multi-stage settlement, and reconciliation platform built for multi-sided marketplaces. It provides mathematical accounting guarantees (Invariants I1 to I8), four hot-account concurrency strategies (S0 Pessimistic, S1 Late-Lock, S2 Striped with N=16 stripes, S4 Ledger-Only), automated bank/provider clearing with dynamic programming subset-sum solving, and dual-control maker-checker dispute resolution.

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

## Core Financial Guarantees (Invariants I1 to I8)

PayCore provides mathematical and architectural invariants verified by property-based testing (1,500 jqwik checks), database triggers, and continuous integrity audits:

| Invariant | Guarantee | Enforcement Mechanism |
| :--- | :--- | :--- |
| **I1: Double-Entry Zero Sum** | $\sum \text{Debits} = \sum \text{Credits}$ across all legs of every journal. | Validated in domain value object and application layer before commit. |
| **I2: Immutability** | Ledger entries cannot be modified or deleted. | PostgreSQL trigger `trg_ledger_entry_immutability` blocks `UPDATE` and `DELETE`. |
| **I3: Global Balance Correctness** | Global debits equal global credits across the whole ledger. | Verified via continuous integrity service (`GET /v1/integrity`). |
| **I4: Balance Derivability** | Cached account balance equals sum of entries (or sum of stripes). | Stripe summation in `account_stripes` table; nightly integrity reconciliation. |
| **I5: Payout Available Balance** | Payout requests never exceed available `SELLER_PAYABLE` (refunds allow negative). | Atomic row lock `SELECT FOR UPDATE` and available balance validation. |
| **I6: Idempotency** | Duplicate client requests with same key and payload return cached response without duplicate posting. | Atomic claim and response caching in `idempotency_keys` table. |
| **I7: Refund Cap** | Total refunds on a payment never exceed original captured gross amount. | Cumulative refund sum verification inside payment posting transaction. |
| **I8: Business Operation Linkage** | Every journal links to at most one business operation and idempotency key. | Unique constraints on `payments.journal_id`, `refunds.journal_id`, `payouts.journal_id`, and `journals(scope, idempotency_key)`. |

---

## Hot-Account Concurrency Architecture

When high-volume marketplaces process transactions, central platform accounts (`PROVIDER_RECEIVABLE` and `PLATFORM_FEE_REVENUE`) suffer write contention. PayCore implements four concurrency strategies per Playbook Section 9:

- **S0 (Pessimistic):** Full-transaction row lock via deterministic account order (`SELECT ... FOR UPDATE`).
- **S1 (Late-Lock):** Row lock deferred to the commit window to reduce lock hold times.
- **S2 (Striped Accounts):** Hot account balance partitioned across $N=16$ balance stripes (`account_stripes`). Writes pick stripes randomly; balance queries aggregate stripes.
- **S4 (Ledger-Only):** Append-only event sourcing with asynchronous snapshot aggregation and zero hot balance mutation on write path.

### Measured Benchmark Results (Workload W2)

Measured against live OpenJDK 21 JVM and PostgreSQL 16.6 via `perf/run-benchmark.py`:

| Strategy | Concurrency ($c=1$) | Concurrency ($c=32$) | Concurrency ($c=128$) | p99 Latency ($c=32$) | Invariants (I1..I8) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **S0 (Pessimistic)** | 233.3 req/s | 457.5 req/s | 464.8 req/s | 171.02 ms | 100% PASS |
| **S1 (Late-Lock)** | 219.7 req/s | 470.7 req/s | 466.6 req/s | 169.37 ms | 100% PASS |
| **S2 (Striped)** | 172.3 req/s | 81.8 req/s | 18.7 req/s | 3,053.08 ms | 100% PASS |
| **S4 (Ledger-Only)** | **305.8 req/s** | **1,900.3 req/s** | **1,650.0 req/s** | **77.42 ms** | **100% PASS** |

*Under $c=32$, Strategy S4 achieves 1,900.3 req/s (a $4.1\times$ throughput increase over baseline S0) with p99 latency of 77.42 ms and zero invariant violations. Detailed distribution tables and evidence logs are preserved in [docs/benchmark-report.md](docs/benchmark-report.md) and [docs/audit/evidence/benchmark-execution.txt](docs/audit/evidence/benchmark-execution.txt).*

---

## Crash Recovery Verification

To verify crash recovery against process termination, PayCore includes an automated crash harness ([scripts/crash-test](scripts/crash-test)):
- Sends mixed Workload W2 traffic over HTTP to the running ledger service.
- Sends a real OS kernel `SIGKILL` (signal 9) to the service process mid-transaction.
- Restarts the service, replays in-flight and succeeded requests with original `Idempotency-Key` headers.
- Asserts zero double-spend, zero duplicate journals, zero unbalanced transactions, and outbox integrity directly in PostgreSQL.

*Test protocol, methodology, and empirical execution results are documented in [docs/crash-test-report.md](docs/crash-test-report.md).*

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
├── perf/
│   ├── k6/                     # Parameterized load tests (W1 uniform, W2 Pareto, W3 hot-spot)
│   └── run-benchmark.py        # Benchmark driver script
├── scripts/
│   └── crash-test              # Real-process 50-cycle JVM & 20-cycle PG SIGKILL crash test harness
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
