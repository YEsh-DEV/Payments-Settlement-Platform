# PayCore: Specification vs Implementation Audit

**Audit Date:** 2026-11-15T20:38:00+05:30  
**Baseline Commit:** `48b365fc4af5a3c435ae98fa9e7c61ad097301bc`  
**Reference Specification:** `PayCore Build Playbook.md`

Status categories:
- **DONE:** Fully implemented and verified with tests.
- **PARTIAL:** Implemented in part; specific edge cases, headers, or parameters missing.
- **DEVIATES:** Implemented but behaves differently from playbook spec.
- **MISSING:** Not implemented in codebase.

---

## 1. Identity Service Features (F-I-01 to F-I-09)

| Item | Status | Evidence (File Path & Test) | Notes |
| :--- | :--- | :--- | :--- |
| **F-I-01: Login (`POST /auth/login`)** | PARTIAL | `services/identity-service/.../controller/AuthController.java`<br>`AuthServiceTest.java:successfulLoginIssuesAccessAndRefreshTokens` | Returns access token in body, sets httpOnly cookie. Cookie path is currently root instead of `/api/identity/auth`. |
| **F-I-02: Refresh (`POST /auth/refresh`)** | DONE | `services/identity-service/.../service/AuthService.java`<br>`AuthServiceTest.java:successfulRefreshRotatesTokenFamily`, `refreshTokenReuseRevokesFamily` | Cookie read, rotated, reuse invalidates token family, audit log recorded. |
| **F-I-03: Logout (`POST /auth/logout`)** | DONE | `services/identity-service/.../service/AuthService.java`<br>`AuthServiceTest.java:logoutDenylistsAccessTokenJti` | Revokes family, clears cookie, writes JTI to Redis deny-list. |
| **F-I-04: Current User (`GET /auth/me`)** | DONE | `services/identity-service/.../controller/AuthController.java`<br>`AuthController.java:me` | Returns id, username, roles, sellerId. |
| **F-I-05: JWKS (`GET /.well-known/jwks.json`)** | DONE | `services/identity-service/.../security/RsaKeyProvider.java`<br>`JwtTokenServiceTest.java:shouldGenerateValidRs256TokensWithKid` | RS256 with `kid`, active and retired keys published. |
| **F-I-06: Service Token (`POST /auth/service-token`)** | DONE | `services/identity-service/.../service/AuthService.java`<br>`AuthServiceTest.java:serviceTokenIssuedWithClientCredentials`<br>`LedgerClientTest.java` | Issues JWT with role `SERVICE`. Proactive token caching and auto-refresh before expiration implemented in `LedgerClient`. |
| **F-I-07: User Admin (ADMIN only)** | DONE | `services/identity-service/.../controller/UserAdminController.java`<br>`UserServiceTest.java:adminCannotDemoteSelf`, `adminCannotDisableSelf` | Create, list, disable, update roles. Self-harm prevention enforced. |
| **F-I-08: Login Protection** | PARTIAL | `services/identity-service/.../service/LoginAttemptService.java`<br>`AuthServiceTest.java:accountLockedAfterFiveFailures` | 5 failures locks username 15 min. Redis token bucket per IP is not yet implemented. |
| **F-I-09: Audit** | DONE | `services/identity-service/.../service/AuthService.java`<br>`V1__init_identity.sql:audit_log` | Records login success/fail, logout, refresh reuse, user update. Immutability trigger active. |

---

## 2. Ledger Service Features (F-L-01 to F-L-13)

| Item | Status | Evidence (File Path & Test) | Notes |
| :--- | :--- | :--- | :--- |
| **F-L-01: Seller Onboarding (`POST /v1/sellers`)** | DONE | `services/ledger-service/.../service/SellerService.java`<br>`SellerController.java` | Provisions `SELLER_PAYABLE` and `SELLER_HELD` accounts; idempotent by sellerId. |
| **F-L-02: Payment Capture (`POST /v1/payments`)** | PARTIAL | `services/ledger-service/.../service/PaymentService.java`<br>`PaymentAndRefundFlowTest.java:testFullCaptureFlow` | Computes fee and seller share. System account used was `USER_FUNDS_IN_TRANSIT` instead of `PROVIDER_RECEIVABLE`. |
| **F-L-03: Refund (`POST /v1/payments/{id}/refunds`)** | DEVIATES | `services/ledger-service/.../service/RefundService.java`<br>`PaymentAndRefundFlowTest.java:testPartialRefund` | Refund logic implemented, but `PostingEngine` currently blocks negative balance on `SELLER_PAYABLE` (F2). |
| **F-L-04: Authorization Holds** | DONE | `services/ledger-service/.../service/HoldService.java`<br>`HoldServiceTest.java` (4 tests) | Create hold, auto-release daemon, manual release, hold capture. |
| **F-L-05: Payouts (Service Only)** | DONE | `services/ledger-service/.../service/PayoutService.java`<br>`LedgerSecurityRoleEnforcementTest.java`<br>`PayoutServiceTest.java` | Lifecycle `INITIATED`/`PAID`/`FAILED` implemented. Enforced via `hasAnyRole('SERVICE', 'ADMIN')`. 409 `invalid_state_transition` on illegal state movements. |
| **F-L-06: Provider Deposit (`POST /v1/provider-deposits`)** | DONE | `services/ledger-service/.../controller/ProviderDepositController.java`<br>`ProviderDepositServiceTest.java`<br>`ReconciliationStage4PostingTest.java` | Debits BANK_CASH, debits PROVIDER_FEE_EXPENSE, credits PROVIDER_RECEIVABLE. Idempotent key `deposit:<id>`. |
| **F-L-07: Adjustments (`POST /v1/adjustments`)** | DONE | `services/ledger-service/.../controller/AdjustmentController.java`<br>`AdjustmentServiceTest.java`<br>`SettlementAdjustmentTest.java` | System accounts (`PROVIDER_RECEIVABLE`, `PROVIDER_FEE_EXPENSE`, `WRITE_OFF_EXPENSE`, `BANK_CASH`). Rejects seller accounts with 422. Retries pending approved adjustments on failure. |
| **F-L-08: Balances & Statements** | PARTIAL | `services/ledger-service/.../service/AccountService.java`<br>`AccountServiceTest.java:testStatementKeysetPagination` | Account statement with keyset pagination exists. `/v1/sellers/{id}/balance` and `/v1/journals/{id}` endpoints missing. |
| **F-L-09: Integrity Checker (`GET /v1/integrity`)** | MISSING | Not present in `ledger-service`. | No `/v1/integrity` endpoint, no nightly job, no `ledger_integrity_violations_total` metric. |
| **F-L-10: Idempotency Framework** | DEVIATES | `services/ledger-service/.../idempotency/IdempotencyService.java`<br>`IdempotencyServiceTest.java` (3 tests) | In-progress returns `concurrent_request` instead of 409 `request_in_progress`; no caller scope; no `Idempotent-Replayed` header; no 7-day cleanup. |
| **F-L-11: Outbox & Events** | DONE | `services/ledger-service/.../service/OutboxRelayService.java`<br>`V6__outbox_claim_support.sql`<br>`OutboxRelayServiceTest.java` | Claims unpublished with `FOR UPDATE SKIP LOCKED` in short transaction, publishes to Kafka (`ledger.domain-events`) outside transaction, updates timestamp. |
| **F-L-12: Rate Limiting** | MISSING | Not present in code. | Redis token bucket filter missing on write endpoints. |
| **F-L-13: Audit** | PARTIAL | `V1__init_ledger.sql:ledger_entries` | Ledger entries have immutable trigger; generic audit log table for all writes missing. |

---

## 3. Settlement Service Features (F-S-01 to F-S-05)

| Item | Status | Evidence (File Path & Test) | Notes |
| :--- | :--- | :--- | :--- |
| **F-S-01: Run Batch (`POST /v1/batches/run`)** | DONE | `services/settlement-service/.../service/BatchCutoffService.java`<br>`BatchCutoffServiceTest.java`<br>`PaymentToSettlementEndToEndIntegrationTest.java` | Cutoff time T, links pending records, acquires PostgreSQL advisory lock `8493021L`, respects min/max payout thresholds. |
| **F-S-02: Request Payout** | DONE | `BatchCutoffService.java`<br>`LedgerClient.java`<br>`PaymentToSettlementEndToEndIntegrationTest.java` | Deterministic payout ID `batchId:sellerId`, calls ledger `POST /v1/payouts` with cached service token. |
| **F-S-03: Send Payout File** | DONE | `BatchCutoffService.java`<br>`BatchController.java`<br>`BatchCutoffServiceTest.java` | Generates simulated bank payout CSV file, transitions payouts and batch to `SENT`. |
| **F-S-04: Confirm or Fail** | DONE | `BatchCutoffService.java`<br>`BatchController.java`<br>`PaymentToSettlementEndToEndIntegrationTest.java` | Ingests bank confirmation CSV, calls ledger confirm/fail, transitions batch to `COMPLETED` or `PARTIAL_FAILURE`. |
| **F-S-05: Payout Views** | DONE | `services/settlement-service/.../controller/BatchController.java` | Lists and filters batches and records with pagination. |

---

## 4. Playbook Invariants (Section 3.4)

| Invariant | Playbook Definition | Codebase Status | Evidence | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **I1** | Every journal has $\ge 2$ legs, positive amounts, and $\sum \text{DR} = \sum \text{CR}$ | PARTIAL | `PostingEngine.java:124`<br>`JournalBalancePropertyTest.java` | Balanced debits/credits tested via jqwik; positive amounts checked. |
| **I2** | Ledger entries are immutable (DB trigger rejects UPDATE/DELETE) | DONE | `V1__init_ledger.sql:trg_ledger_entry_immutability` | Trigger rejects updates and deletes with SQLSTATE `PAY02`. |
| **I3** | Globally, total debits equal total credits | PARTIAL | `PostingEngine.java` | Holds transitively from I1, but lacks an automated global query test or integrity checker. |
| **I4** | Cached account balance equals sum of entries (striped: sum of stripes) | PARTIAL | `S2StripedStrategy.java`, `S4LedgerOnlyStrategy.java` | Implemented in strategies, but lacks formal automated test comparing cached balance to $\sum \text{entries}$. |
| **I5** | Payout requests never exceed available SELLER_PAYABLE | DEVIATES | `PostingEngine.java:189` | Code blocks negative balance on ALL journals (including refunds), violating playbook spec. |
| **I6** | Request with given idempotency key produces at most one journal | DONE | `PostingEngine.java:95`<br>`PostingEngineTest.java:testIdempotentReplayReturnsExistingJournal` | Verified in unit tests. |
| **I7** | Total refunds for a payment never exceed captured amount | DONE | `services/ledger-service/.../service/RefundService.java`<br>`PaymentAndRefundFlowTest.java:testRefundExceedingCapturedAmountRejected` | Verified by unit test. |
| **I8** | Each journal links to exactly one business operation (unique constraint) | PARTIAL | `V1__init_ledger.sql:payments`, `refunds` | Unique foreign keys exist on payments and refunds, but missing on holds and payouts. |

---

## 5. Success Criteria (S1 to S6)

| Criteria | Playbook Requirement | Status | Evidence | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **S1** | Property and concurrency tests prove debits equal credits after any sequence | PARTIAL | `JournalBalancePropertyTest.java` (1,500 checks) | Property test exists; multi-threaded random sequence concurrency test needed. |
| **S2** | Crash test (kill service mid-load, restart, re-check invariants) passes repeatedly | DEVIATES | `tests/crash-harness/sigkill-crash-test.sh` | Script only queried static DB; did not kill service mid-load or replay in-flight requests (F11). |
| **S3** | Benchmark report compares $\ge 3$ strategies on same workload and hardware | DEVIATES | `docs/hot-account-report.md` | Numbers in report were synthetic/analytical; missing raw output JSONs in `perf/results/` (F5). |
| **S4** | Reconciliation matches seeded synthetic data and reports match rate per rule | PARTIAL | `ReconciliationEngineTest.java` | Overall match rate calculated; per-fault precision and recall report across 20+ seeds needed. |
| **S5** | `docker compose up` brings whole system up with seed data; console works end to end | PARTIAL | `deploy/docker-compose.yml` | Stack runs, but `scripts/seed` missing; console API routes need alignment with nginx proxy. |
| **S6** | CI is green: format check, unit tests, integration tests, coverage gate | PARTIAL | `.github/workflows/ci.yml` | Build and Spotless green; JaCoCo coverage gate, SpotBugs, ArchUnit, and Playwright smoke missing. |

---

## 6. Playbook Acceptance Checklist (Section 19.1)

| Checklist Item | Status | Evidence / Location |
| :--- | :--- | :--- |
| `docker compose --profile core up --build` starts system; `scripts/seed` loads demo data; console works | PARTIAL | Compose file exists; `scripts/seed` missing. |
| CI green on main: format, unit, integration, coverage gate, frontend, Docker builds | PARTIAL | Format, unit, frontend pass; coverage gate and Docker build job missing. |
| Invariant tests I1 to I8 pass for every strategy | PARTIAL | Parameterized strategy test suite covering I1–I8 needed. |
| `docs/benchmark-report.md` and `docs/crash-test-report.md` exist, generated by scripts | MISSING | Reports are hand-written; scripts and raw JSON outputs missing. |
| Reconciliation accuracy report exists with per-fault precision and recall | MISSING | Report not yet generated across multiple seeds. |
| OpenAPI documents available for all three services; contract test in CI | PARTIAL | Springdoc configured; contract test missing. |
| `THIRD_PARTY_NOTICES.md` complete; README credits base repos & lists original work | DONE | Notices file complete; README updated. |
| No secrets, default passwords, or leftover pulled demo code in repo or history | PARTIAL | Repo clean of external author packages; dev default passwords need gating for prod profile. |
| `docs/runbook.md`, `docs/architecture.md` and all required ADRs written | PARTIAL | Runbook and ADR-001–005 exist; `docs/architecture.md` and new ADRs needed. |
| Every README number matches a generated report | PARTIAL | Numbers currently in README must be replaced with outputs from generated reports. |
