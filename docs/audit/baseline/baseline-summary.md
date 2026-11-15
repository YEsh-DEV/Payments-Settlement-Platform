# PayCore Baseline Audit Summary

**Date:** 2026-11-15T20:36:00+05:30  
**Commit SHA:** `48b365fc4af5a3c435ae98fa9e7c61ad097301bc`  
**Branch:** `wp-a-truth-audit` (branched from `main`)  
**Environment:**
- **JVM Version:** OpenJDK 21.0.13+9-LTS (Temurin-21.0.13+9, 64-Bit Server VM, mixed mode, sharing)
- **OS:** Linux omarchy 7.2.15-1-omarchy #1 SMP PREEMPT_DYNAMIC Mon, 02 Nov 2026 10:32:17 +0000 x86_64 GNU/Linux
- **Maven:** Apache Maven 3.10.0 (via `./mvnw` wrapper)
- **Node.js:** v26.12.0
- **NPM:** 11.20.0

---

## 1. Maven Reactor Verification (`./mvnw -B clean verify`)

Full log output saved in: `docs/audit/baseline/mvn-verify-baseline.log`

| Module | Test Count | Failures | Errors | Skipped | Status | Elapsed Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `paycore-parent` | 0 | 0 | 0 | 0 | SUCCESS | 0.090 s |
| `paycore-common` | 7 | 0 | 0 | 0 | SUCCESS | 2.042 s |
| `identity-service` | 16 | 0 | 0 | 0 | SUCCESS | 6.568 s |
| `ledger-service` | 31 (+1,500 jqwik) | 0 | 0 | 0 | SUCCESS | 4.941 s |
| `settlement-service` | 23 | 0 | 0 | 0 | SUCCESS | 3.431 s |
| **Total Reactor** | **77** | **0** | **0** | **0** | **BUILD SUCCESS** | **17.285 s** |

### Individual Test Breakdown
- **`paycore-common` (7 tests):**
  - `MoneyTest`: 5 tests (`zeroMoneyShouldHaveZeroPaise`, `positivePaiseShouldConstructProperly`, `negativePaiseShouldThrow`, `additionAndSubtractionShouldPreservePrecision`, `calculateFeeBpsShouldPerformRoundHalfUp`)
  - `RequestIdFilterTest`: 2 tests (`incomingRequestIdShouldBePropagated`, `missingRequestIdShouldBeGenerated`)
- **`identity-service` (16 tests):**
  - `JwtTokenServiceTest`: 3 tests (`shouldGenerateValidRs256TokensWithKid`, `shouldValidateTokenSuccessfully`, `shouldRejectTamperedToken`)
  - `AuthServiceTest`: 7 tests (`successfulLoginIssuesAccessAndRefreshTokens`, `invalidPasswordThrowsInvalidCredentials`, `accountLockedAfterFiveFailures`, `successfulRefreshRotatesTokenFamily`, `refreshTokenReuseRevokesFamily`, `logoutDenylistsAccessTokenJti`, `serviceTokenIssuedWithClientCredentials`)
  - `UserServiceTest`: 6 tests (`createUserSuccess`, `createUserDuplicateUsernameFails`, `adminCannotDemoteSelf`, `adminCannotDisableSelf`, `adminCanUpdateOtherUsers`, `listUsersReturnsAll`)
- **`ledger-service` (31 tests + 1,500 jqwik property checks):**
  - `JournalBalancePropertyTest`: 2 jqwik properties (1,000 randomized payment splits, 500 multi-seller composite splits)
  - `PostingEngineTest`: 6 tests (Balanced posting, unbalanced 400 rejection, negative leg 400 rejection, insufficient funds 422 rejection, idempotency key replay caching, global lock ordering)
  - `PaymentAndRefundFlowTest`: 4 tests (Full capture flow, partial refund, 100% full refund, refund exceeding capture rejected)
  - `HoldServiceTest`: 4 tests (Create hold, hold locks available funds, capture hold, release hold)
  - `PayoutServiceTest`: 3 tests (Initiate payout, confirm payout, fail payout with reversal)
  - `AdjustmentServiceTest`: 2 tests (Operational adjustment credit, dispute adjustment debit)
  - `AccountServiceTest`: 2 tests (Get balance, keyset pagination statement)
  - `IdempotencyServiceTest`: 3 tests (New request executes, same key replays, payload mismatch throws 422)
  - `HotAccountStrategiesTest`: 5 tests (S0, S1, S2, S4 execution and strategy router routing)
  - `SigkillRecoveryVerificationTest`: 2 tests (Integrity audit, crash recovery invariant check)
- **`settlement-service` (23 tests):**
  - `SettlementCandidateConsumerTest`: 2 tests (Ingest candidate, duplicate event deduped via `processed_events`)
  - `BatchCutoffServiceTest`: 3 tests (Run cutoff, pending linked, zero payouts for empty pending)
  - `StreamingFileParserTest`: 3 tests (Stream valid CSV, reject duplicate hash, reject trailer mismatch)
  - `ProviderSimulatorTest`: 1 test (Generate file with faults)
  - `ReconciliationEngineTest`: 4 tests (Exact match + fee variance, fuzzy fallback, subset-sum deposit match, ground truth evaluation)
  - `SubsetSumSolverTest`: 3 tests (Exact subset found, ambiguous subset flagged, no match)
  - `SlaEscalationDaemonTest`: 1 test (Breached case escalated and severity elevated)
  - `SettlementAdjustmentTest`: 3 tests (Propose adjustment, approve adjustment resolves case, self-approval rejected with `MakerCheckerViolationException`)
  - `CaseServiceTest`: 3 tests (Create case, assign case, optimistic locking conflict throws 409)

---

## 2. Frontend Build Verification (`npm ci && npm run build`)

Full log output saved in: `docs/audit/baseline/npm-build-baseline.log`

- **Node Module Installation:** 288 packages installed and audited in 11s.
- **TypeScript Compilation:** `tsc -b` passed with 0 errors.
- **Vite Production Bundler:** Built in 1.03s, transforming 2,566 modules.
- **Code-Split Bundles Generated:**
  - `dist/index.html` (1.72 kB)
  - `dist/assets/index.css` (59.75 kB)
  - Code-split route chunks: `DashboardPage`, `CasesPage`, `CaseDetailPage`, `ReconciliationPage`, `LedgerPage`, `PayoutsPage`, `MerchantPortalPage`, `SystemPage`, `UsersPage`, `AuditLogsPage`.
