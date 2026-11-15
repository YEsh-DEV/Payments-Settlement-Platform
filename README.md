# Payments-Settlement-Platform

**PayCore** is a high-throughput, fault-tolerant, double-entry financial ledger and settlement platform for multi-sided marketplace platforms, built with Java 21, Spring Boot 3.5.x, PostgreSQL 16, Redis 7, Apache Kafka (KRaft), and React 19 / TypeScript.

## Architecture & System Design
- [PayCore Build Playbook](PayCore%20Build%20Playbook.md) - Single source of truth specifications
- [System Design Document](system-design.md) - End-to-end architecture and implementation blueprint
- [Third-Party Notices](THIRD_PARTY_NOTICES.md) - Open-source attribution and licensing details
- [Architectural Decision Records (ADRs)](docs/adr/) - Technical decisions and trade-offs

## Attribution
Built on top of MIT-licensed:
- `Dedmoo/RetailBankingCore` (Outbox messaging, Redis token bucket rate limiting, and Testcontainers support)
- `Gabrielab5/IssuesFlow` (React 19 / Vite UI components, scheduled escalation pattern, and token deny-list structure)

Original implementations include:
1. Journal-based double-entry posting engine and Chart of Accounts model.
2. Four hot-account concurrency strategies (`PESSIMISTIC`, `LATE_LOCK`, `STRIPED`, `LEDGER_ONLY`) with reproducible benchmark harness.
3. 50-iteration `SIGKILL` crash and recovery test suite.
4. RS256 dual-key JWKS identity service with refresh token family rotation and service tokens.
5. Settlement service: candidate event ingestion, cutoff batching, streaming file ingestion, and provider simulator.
6. Reconciliation engine with 4-stage matching (including dynamic programming / meet-in-the-middle subset sum).
7. Case management with SLA auto-escalation daemon and maker-checker adjustment workflow.
8. Role-based Ops Console with in-memory token security and silent cookie refresh.
9. Comprehensive Invariants (I1-I8) automated verification suite.

## Development & Build
Requirements:
- Java 21 LTS
- Maven 3.9+
- Docker & Docker Compose
- Node.js 24+ / NPM 11+
