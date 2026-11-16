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
| **F-I-01: Login (`POST /auth/login`)** | DONE | `AuthController.java`<br>`AuthControllerTest.testLoginSuccess`<br>`AuthControllerTest.testLoginInvalidCredentialsFails` | Returns access token in body, sets httpOnly refresh cookie. |
| **F-I-02: Refresh (`POST /auth/refresh`)** | DONE | `AuthService.java`<br>`AuthServiceTest.successfulRefreshRotatesTokenFamily`<br>`AuthServiceTest.refreshTokenReuseRevokesFamily` | Cookie read, rotated, reuse invalidates token family, audit log recorded. |
| **F-I-03: Logout (`POST /auth/logout`)** | DONE | `AuthController.java`, `AuthService.java`<br>`AuthControllerTest.testLogoutSuccess`<br>`AuthServiceTest.logoutDenylistsAccessTokenJti` | Revokes family, clears cookie, writes JTI to Redis deny-list. |
| **F-I-04: Current User (`GET /auth/me`)** | DONE | `AuthController.java`<br>`AuthControllerTest.testCurrentUserReturnsUserProfile`<br>`AuthControllerTest.testCurrentUserUnauthenticatedFails` | Returns id, username, roles, sellerId. Enforced by Spring Security. |
| **F-I-05: JWKS (`GET /.well-known/jwks.json`)** | DONE | `RsaKeyProvider.java`<br>`JwtTokenServiceTest.shouldGenerateValidRs256TokensWithKid`<br>`OpenApiContractTest.testOpenApiSpecContainsRequiredEndpoints` | RS256 with `kid`, active and retired keys published. |
| **F-I-06: Service Token (`POST /auth/service-token`)** | DONE | `AuthService.java`<br>`AuthServiceTest.serviceTokenIssuedWithClientCredentials`<br>`LedgerClientTest.java` | Issues JWT with role `SERVICE`. Proactive token caching and auto-refresh before expiration implemented in `LedgerClient`. |
| **F-I-07: User Admin (ADMIN only)** | DONE | `UserAdminController.java`<br>`UserAdminControllerTest.testCreateUserSuccess`<br>`UserAdminControllerTest.testAdminCannotDisableSelf` | Create, list, disable, update roles. Self-harm prevention enforced. |
| **F-I-08: Login Protection** | DONE | `LoginAttemptService.java`<br>`AuthServiceTest.accountLockedAfterFiveFailures`<br>`LoginAttemptServiceTest` | 5 failures locks username 15 min with Redis tracking. |
| **F-I-09: Audit** | DONE | `AuthService.java`<br>`V1__init_identity.sql:audit_log`<br>`AuthServiceTest.testAuditLogRecordedOnAuthEvent` | Records login success/fail, logout, refresh reuse, user update. Immutability trigger active. |

---

## 2. Ledger Service Features (F-L-01 to F-L-13)

| Item | Status | Evidence (File Path & Test) | Notes |
| :--- | :--- | :--- | :--- |
| **F-L-01: Seller Onboarding (`POST /v1/sellers`)** | DONE | `SellerService.java`<br>`SellerServiceTest.testOnboardSellerSuccess`<br>`SellerServiceTest.testOnboardDuplicateSellerFails` | Provisions `SELLER_PAYABLE` and `SELLER_HELD` accounts; idempotent by sellerId. |
| **F-L-02: Payment Capture (`POST /v1/payments`)** | DONE | `PaymentService.java`<br>`PaymentServiceTest.testCapturePaymentSuccess`<br>`PaymentServiceTest.testCapturePaymentZeroAmountFails` | Computes fee and seller share. System account `PROVIDER_RECEIVABLE` used. |
| **F-L-03: Refund (`POST /v1/payments/{id}/refunds`)** | DONE | `RefundController.java`, `RefundService.java`<br>`PaymentAndRefundFlowTest.testPartialRefund`<br>`PaymentAndRefundFlowTest.testRefundExceedingCapturedAmountRejected` | Full and partial refunds debited from merchant payable or held. Registered in OpenAPI schema. |
| **F-L-04: Authorization Holds** | DONE | `HoldService.java`<br>`HoldServiceTest.testCreateHoldSuccess`<br>`HoldServiceTest.testHoldExpiredAutoReleased` | Create hold, auto-release daemon, manual release, hold capture. |
| **F-L-05: Payouts (Service Only)** | DONE | `PayoutService.java`<br>`LedgerSecurityRoleEnforcementTest.testServiceRoleCanAccessPayouts`<br>`LedgerSecurityRoleEnforcementTest.testUnauthorizedRoleRejected` | Lifecycle `INITIATED`/`PAID`/`FAILED`. Enforced via `hasAnyRole('SERVICE', 'ADMIN')`. 409 `invalid_state_transition` on illegal state movements. |
| **F-L-06: Provider Deposit (`POST /v1/provider-deposits`)** | DONE | `ProviderDepositService.java`<br>`ProviderDepositServiceTest.testProviderDepositSuccess`<br>`ProviderDepositServiceTest.testNegativeDepositFails` | Debits BANK_CASH, debits PROVIDER_FEE_EXPENSE, credits PROVIDER_RECEIVABLE. Idempotent key `deposit:<id>`. |
| **F-L-07: Adjustments (`POST /v1/adjustments`)** | DONE | `AdjustmentController.java`<br>`AdjustmentServiceTest.testCreateAdjustmentSuccess`<br>`AdjustmentServiceTest.testAdjustmentWithSellerAccountFails422` | System accounts (`PROVIDER_RECEIVABLE`, `PROVIDER_FEE_EXPENSE`, `WRITE_OFF_EXPENSE`, `BANK_CASH`). Rejects seller accounts with 422. Retries pending approved adjustments on failure. |
| **F-L-08: Balances & Statements** | DONE | `AccountService.java`<br>`AccountServiceTest.testStatementKeysetPagination`<br>`AccountServiceTest.testAccountNotFoundFails` | Account statement with keyset pagination and balance aggregation. |
| **F-L-09: Integrity Checker (`GET /v1/integrity`)** | IN_PROGRESS | `docs/audit/spec-vs-implementation.md` | Core ledger integrity verified via jqwik property tests (`JournalBalancePropertyTest`). Full endpoint scheduled in WP-I. |
| **F-L-10: Idempotency Framework** | DONE | `IdempotencyService.java`<br>`IdempotencyServiceTest.testIdempotentReplayReturnsExistingJournal`<br>`IdempotencyServiceTest.testConcurrentInFlightReturns409` | Atomic insert with `IN_FLIGHT`, returns 409 `idempotency_conflict` with `Retry-After`, and marks `COMPLETED` on commit. |
| **F-L-11: Outbox & Events** | DONE | `OutboxRelayService.java`<br>`OutboxRelayServiceTest.testSuccessfulRelay`<br>`OutboxRelayServiceTest.testKafkaFailureStopsBatch` | Claims unpublished with `FOR UPDATE SKIP LOCKED` in short transaction, publishes to Kafka (`ledger.domain-events`) outside transaction, updates timestamp. |
| **F-L-12: Rate Limiting** | IN_PROGRESS | Handled in API gateway / edge. | Rate limiting filter pattern scheduled for production hardening. |
| **F-L-13: Audit** | DONE | `V1__init_ledger.sql:trg_ledger_entry_immutability`<br>`PostingEngineTest` | Ledger entries have immutable trigger rejecting update/delete with SQLSTATE `PAY02`. |

---

## 3. Settlement Service Features (F-S-01 to F-S-05)

| Item | Status | Evidence (File Path & Test) | Notes |
| :--- | :--- | :--- | :--- |
| **F-S-01: Run Batch (`POST /v1/batches/run`)** | DONE | `BatchCutoffService.java`<br>`BatchControllerTest.testRunCutoff`<br>`BatchCutoffServiceTest.testCutoffWithNoPendingRecords` | Cutoff time T, links pending records, acquires PostgreSQL advisory lock `8493021L`, respects min/max payout thresholds. |
| **F-S-02: Request Payout** | DONE | `BatchCutoffService.java`, `LedgerClient.java`<br>`PaymentToSettlementEndToEndIntegrationTest.testPayoutRequested`<br>`PaymentToSettlementEndToEndIntegrationTest.testLedgerUnavailableHandled` | Deterministic payout ID `batchId:sellerId`, calls ledger `POST /v1/payouts` with cached service token. |
| **F-S-03: Send Payout File** | DONE | `StreamingFileParser.java`, `SettlementFileController.java`<br>`SettlementFileControllerTest.testUploadFile`<br>`StreamingFileParserTest.testMalformedCsvFails` | Streaming ingestion of bank CSV, SHA-256 deduplication, idempotent upsert of provider settlement lines. |
| **F-S-04: Confirm or Fail** | DONE | `ReconciliationEngine.java`<br>`GoldenFixturesReconciliationTest.testAllGoldenFixtures`<br>`ReconciliationAccuracyRunnerTest.testRunAccuracyEvaluationAcrossSeeds` | Ingests bank confirmation CSV, runs 4-stage reconciliation engine, resolves 1-to-1, subset sum, tolerance matching, and emits discrepancy cases. |
| **F-S-05: Payout Views & Case Management** | DONE | `CaseController.java`, `BatchController.java`<br>`CaseControllerTest.testListCases`<br>`CaseControllerTest.testGetNonExistentCaseThrows`<br>`CaseControllerTest.testAssignCase`<br>`BatchControllerTest.testGetBatch` | Lists and filters batches, settlement files, and discrepancy cases with keyset/page pagination and optimistic locking versioning. |

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
