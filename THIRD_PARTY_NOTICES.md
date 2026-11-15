# Third-Party Notices & Attribution

This document records all third-party open-source software libraries, frameworks, and reference implementations incorporated or adapted into PayCore, in strict accordance with the PayCore Build Playbook (Section 0 Rule 3 and Section 6).

---

## 1. RetailBankingCore

- **Repository:** `https://github.com/Dedmoo/RetailBankingCore.git`
- **Commit SHA:** `955c2f7aa83a8df7d30ad1303be5be6ab079e6e8`
- **Original Author:** Mehmet Serin
- **License:** MIT License

### Original License Notice
```text
MIT License

Copyright (c) 2026 Mehmet Serin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Files Pulled, Adapted & New Locations

| Source File in `RetailBankingCore` | Adaptations Made | New Location in `paycore` |
| :--- | :--- | :--- |
| `src/main/java/com/mehmetserin/banking/messaging/OutboxEvent.java` | Package renamed; payload updated to PayCore standard event envelope. | `services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/messaging/OutboxEvent.java` |
| `src/main/java/com/mehmetserin/banking/messaging/OutboxEventRepository.java` | Package renamed; kept native SQL query with `FOR UPDATE SKIP LOCKED`. | `services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/messaging/OutboxEventRepository.java` |
| `src/main/java/com/mehmetserin/banking/messaging/OutboxRelay.java` | Package renamed; target topic set to `ledger.domain-events`; supports manual relay execution in tests. | `services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/messaging/OutboxRelay.java` |
| `src/main/java/com/mehmetserin/banking/messaging/OutboxPoisonMonitor.java` | Package renamed; monitored attempt counts updated. | `services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/messaging/OutboxPoisonMonitor.java` |
| `src/main/java/com/mehmetserin/banking/messaging/MessagingProperties.java` | Package renamed; updated configuration prefix to `paycore.messaging`. | `services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/messaging/MessagingProperties.java` |
| `src/main/java/com/mehmetserin/banking/ratelimit/RedisTokenBucketRateLimiter.java` | Package renamed; Lua token bucket preserved; adapted to token subject keys. | `services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/ratelimit/RedisTokenBucketRateLimiter.java` |
| `src/main/java/com/mehmetserin/banking/ratelimit/RateLimitFilter.java` | Protected paths updated to `/v1/payments`, `/v1/payments/*/refunds`, `/v1/holds`, `/v1/payouts`. | `services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/ratelimit/RateLimitFilter.java` |
| `src/main/java/com/mehmetserin/banking/ratelimit/RateLimitBackendException.java` | Mapped to RFC 9457 Problem Details (503 Service Unavailable). | `services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/ratelimit/RateLimitBackendException.java` |
| `src/main/java/com/mehmetserin/banking/common/RequestIdFilter.java` | Moved to common library; extracts/generates `X-Request-Id` and binds to MDC. | `libs/paycore-common/src/main/java/io/github/yesh/paycore/common/web/RequestIdFilter.java` |
| `src/main/java/com/mehmetserin/banking/config/SecurityHeadersFilter.java` | Moved to common library; applies OWASP recommended security headers. | `libs/paycore-common/src/main/java/io/github/yesh/paycore/common/security/SecurityHeadersFilter.java` |
| `src/test/java/com/mehmetserin/banking/support/PostgresIntegrationSupport.java` | Shared singleton Testcontainers setup for PostgreSQL 16, Redis 7, Kafka KRaft. | `services/ledger-service/src/test/java/io/github/yesh/paycore/ledger/support/PostgresIntegrationSupport.java` |

### Explicitly Excluded / Deleted Files
- `DomainEventLoggingConsumer.java` (Demo artifact only).
- `HouseFundingService.java` (Retail concept not applicable to marketplace).
- `com/mehmetserin/banking/auth/*` (Completely replaced by `identity-service`).
- `com/mehmetserin/banking/user/*` (Completely replaced by `identity-service`).
- `db/migration/V1__*.sql` through `V5__*.sql` (Replaced by PayCore Flyway schemas).
- Retail account freeze, unfreeze, and retail reversal naming.

---

## 2. IssuesFlow

- **Repository:** `https://github.com/Gabrielab5/IssuesFlow.git`
- **Commit SHA:** `ef4bd84a2e02926c5fce8f070dfc362fd07c78bf`
- **Original Author:** Gabrielab5
- **License:** MIT License (Declared in repository README.md: "This project is MIT licensed.")

### Original License Notice
```text
MIT License

Copyright (c) 2026 Gabrielab5

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Files Pulled, Adapted & New Locations

| Source File in `IssuesFlow` | Adaptations Made | New Location in `paycore` |
| :--- | :--- | :--- |
| `src/main/java/com/att/tdp/issueflow/scheduling/TicketEscalationScheduler.java` | Reimplemented for Case SLA management; runs per-minute; handles overdue cases, bumps severity, extends SLA, emits outbox event. | `services/settlement-service/src/main/java/io/github/yesh/paycore/settlement/cases/CaseEscalationDaemon.java` |
| `src/main/java/com/att/tdp/issueflow/security/TokenDenyListService.java` | Adapted from relational DB table to Redis key-value store with TTL matching remaining JWT lifetime. | `services/identity-service/src/main/java/io/github/yesh/paycore/identity/security/TokenDenyListService.java` |
| `src/main/java/com/att/tdp/issueflow/audit/AuditService.java` & `AuditLog.java` | Adapted to PayCore immutable audit table structure with JSONB payload. | `services/identity-service/src/main/java/io/github/yesh/paycore/identity/audit/AuditService.java` |
| `frontend/src/components/ui/` (`button.tsx`, `card.tsx`, `badge.tsx`, `input.tsx`, `select.tsx`, `dialog.tsx`, `loading.tsx`, `textarea.tsx`) | Clean Tailwind CSS v4 UI components reused for PayCore console. | `web/ops-console/src/components/ui/` |
| `frontend/src/components/tickets/KanbanBoard.tsx` & `KanbanColumn.tsx` | Adapted columns from ticket states to Case states (`OPEN`, `IN_REVIEW`, `ESCALATED`, `RESOLVED`, `WRITTEN_OFF`); integrated with TanStack Query. | `web/ops-console/src/components/cases/CaseKanbanBoard.tsx` |
| `frontend/src/api/client.ts` | **Critical Security Fix:** Replaced `localStorage` access token storage with in-memory state; added single-flight silent refresh mutex via `httpOnly` cookie. | `web/ops-console/src/api/client.ts` |
| `frontend/nginx.conf` | Adapted proxy paths for `/api/identity/`, `/api/ledger/`, `/api/settlement/`. | `deploy/nginx.conf` |

### Explicitly Excluded / Deleted Files
- Ticket, project, comment, attachment, mention, and import/export CSV packages.
- Hardcoded `admin123` credentials and test seed migrations.
- Frontend Jira/Atlassian branding, animated hero background, and ticket dependency views.
- Developer instruction documentation (`AGENTS.md`, `Claude.md`, `prompts.md`, `Instructions.md`, `SETUP.md`, `run.md`).
