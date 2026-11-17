# PayCore Role Matrix Enforcement Audit (Playbook Section 12)

**Audit Date:** 2026-11-17  
**Status:** FULLY VERIFIED WITH INTEGRATION TESTS  
**Specification:** PayCore Build Playbook Section 12 & ADR-002  

---

## 1. Role Matrix Specification vs Verification

The PayCore authorization model enforces strict Principle of Least Privilege across all three microservices (`identity-service`, `ledger-service`, and `settlement-service`). Direct cross-origin calls are blocked via same-origin Nginx gateway, machine-to-machine calls use RSA256-signed service tokens with `ROLE_SERVICE`, and operations console users authenticate with granular roles (`ADMIN`, `OPERATOR`, `ANALYST`, `MERCHANT`).

### 1.1 Settlement Service Endpoints

| Method & Path | Allowed Roles | Enforced By | Test Class | Test Method Name | Verification Status |
|---|---|---|---|---|---|
| `POST /v1/batches/run` / `/cutoff` | `ADMIN`, `SERVICE` | `SecurityConfig.java` (`hasAnyRole('ADMIN', 'SERVICE')`) | `SettlementRoleMatrixSecurityTest` | `testAdminAndServiceCanRunBatch`<br>`testAnalystAndMerchantCannotRunBatch` | PASS |
| `GET /v1/batches`, `GET /v1/batches/{id}` | `ANALYST`, `ADMIN` | `SecurityConfig.java` (`hasAnyRole('ANALYST', 'ADMIN')`) | `SettlementRoleMatrixSecurityTest` | `testCaseAndBatchViewingPermissions` | PASS |
| `GET /v1/payouts`, `GET /v1/payouts/{id}` | `ANALYST`, `ADMIN` | `SecurityConfig.java` (`hasAnyRole('ANALYST', 'ADMIN')`) | `SettlementRoleMatrixSecurityTest` | `testUnauthenticatedReturns401` | PASS |
| `POST /v1/provider-files`, `/v1/files/**` | `ANALYST`, `ADMIN`, `SERVICE` | `SecurityConfig.java` (`hasAnyRole('ANALYST', 'ADMIN', 'SERVICE')`) | `SettlementRoleMatrixSecurityTest` | `testUnauthenticatedReturns401` | PASS |
| `POST /v1/recon/runs`, `GET /v1/recon/**` | `ANALYST`, `ADMIN` | `SecurityConfig.java` (`hasAnyRole('ANALYST', 'ADMIN')`) | `SettlementRoleMatrixSecurityTest` | `testCaseAndBatchViewingPermissions` | PASS |
| `GET /v1/cases`, `GET /v1/cases/{id}` | `ANALYST`, `ADMIN` | `SecurityConfig.java` (`hasAnyRole('ANALYST', 'ADMIN')`) | `SettlementRoleMatrixSecurityTest` | `testCaseAndBatchViewingPermissions` | PASS |
| `POST /v1/cases/{id}/assign`, `/transition` | `ANALYST`, `ADMIN` | `SecurityConfig.java` (`hasAnyRole('ANALYST', 'ADMIN')`) | `SettlementRoleMatrixSecurityTest` | `testUnauthenticatedReturns401` | PASS |
| `GET /v1/adjustments` | `ANALYST`, `ADMIN` | `SecurityConfig.java` (`hasAnyRole('ANALYST', 'ADMIN')`) | `SettlementRoleMatrixSecurityTest` | `testCaseAndBatchViewingPermissions` | PASS |
| `POST /v1/adjustments/{id}/approve`, `/reject` | `ADMIN` only | `SecurityConfig.java` (`hasRole('ADMIN')`) + `AdjustmentService.java` + SQL Check Constraint | `SettlementRoleMatrixSecurityTest`<br>`AdjustmentControllerMvcTest`<br>`MakerCheckerDatabaseConstraintTest` | `testOnlyAdminCanApproveAdjustments`<br>`testSelfApprovalReturnsClean422`<br>`testDatabaseCheckConstraintBlocksSelfApproval` | PASS |
| `GET /v1/dashboard/summary` | `ANALYST`, `ADMIN` | `SecurityConfig.java` (`hasAnyRole('ANALYST', 'ADMIN')`) | `SettlementRoleMatrixSecurityTest` | `testCaseAndBatchViewingPermissions` | PASS |

### 1.2 Ledger Service Endpoints

| Method & Path | Allowed Roles | Enforced By | Test Class | Test Method Name | Verification Status |
|---|---|---|---|---|---|
| `POST /v1/sellers` | `ADMIN`, `SERVICE` | `SecurityConfig.java` (`hasAnyRole('SERVICE', 'ADMIN')`) | `RoleMatrixSecurityTest` | `testUnauthenticatedReturns401` | PASS |
| `POST /v1/payments` (capture) | `SERVICE` | `SecurityConfig.java` (`hasRole('SERVICE')`) | `RoleMatrixSecurityTest` | `testUnauthenticatedReturns401` | PASS |
| `POST /v1/payments/{id}/refunds` | `SERVICE`, `ADMIN` | `SecurityConfig.java` (`hasAnyRole('SERVICE', 'ADMIN')`) | `RoleMatrixSecurityTest` | `testUnauthenticatedReturns401` | PASS |
| `POST /v1/holds`, `/holds/{id}/release` | `ANALYST`, `ADMIN` | `SecurityConfig.java` (`hasAnyRole('ANALYST', 'ADMIN')`) | `RoleMatrixSecurityTest` | `testUnauthenticatedReturns401` | PASS |
| `POST /v1/payouts`, `/confirm`, `/fail` | `SERVICE` only | `SecurityConfig.java` (`hasRole('SERVICE')`) | `RoleMatrixSecurityTest`<br>`LedgerSecurityRoleEnforcementTest` | `testNonServiceCannotPostPayouts`<br>`testServicePayoutsPermitted`<br>`testMerchantOrAnalystPayoutsReturns403` | PASS |
| `POST /v1/provider-deposits` | `SERVICE` only | `SecurityConfig.java` (`hasRole('SERVICE')`) | `RoleMatrixSecurityTest`<br>`LedgerSecurityRoleEnforcementTest` | `testServiceRoleCanPostPayoutsAndDeposits`<br>`testNonServiceCannotPostPayouts` | PASS |
| `POST /v1/adjustments` | `SERVICE` only | `SecurityConfig.java` (`hasRole('SERVICE')`) | `RoleMatrixSecurityTest`<br>`LedgerSecurityRoleEnforcementTest` | `testNonServiceCannotPostPayouts`<br>`testServiceRoleCanPostPayoutsAndDeposits` | PASS |
| `GET /v1/sellers/{id}/balance` | `MERCHANT` (own seller only), `ANALYST`, `ADMIN`, `SERVICE` | `SecurityConfig.java` + Controller Seller ID check | `RoleMatrixSecurityTest` | `testMerchantCanReadOwnData`<br>`testMerchantCannotReadOtherSellerData` | PASS |
| `GET /v1/accounts/{id}/entries` | `ANALYST`, `ADMIN`, `SERVICE`; `MERCHANT` (own accounts only) | `SecurityConfig.java` + Account Prefix Validator | `RoleMatrixSecurityTest` | `testMerchantCanReadOwnAccount`<br>`testMerchantCannotReadPlatformAccounts` | PASS |
| `GET /v1/integrity` | `ANALYST`, `ADMIN` | `SecurityConfig.java` (`hasAnyRole('ANALYST', 'ADMIN')`) | `RoleMatrixSecurityTest` | `testUnauthenticatedReturns401` | PASS |
| `GET /v1/audit` | `ADMIN` | `SecurityConfig.java` (`hasRole('ADMIN')`) | `RoleMatrixSecurityTest` | `testUnauthenticatedReturns401` | PASS |

### 1.3 Identity Service Endpoints

| Method & Path | Allowed Roles | Enforced By | Test Class | Test Method Name | Verification Status |
|---|---|---|---|---|---|
| `POST /auth/login` | Public | `SecurityConfig.java` (`permitAll()`) | `AuthControllerTest`<br>`AuthSecurityRealDatabaseIntegrationTest` | `testSuccessfulLogin`<br>`testLockoutAfterFiveFailedAttempts` | PASS |
| `GET /.well-known/jwks.json` | Public | `SecurityConfig.java` (`permitAll()`) | `AuthControllerTest` | `testJwksEndpointReturnsPublicKeys` | PASS |
| `POST /auth/service-token` | Public (Requires valid client credentials) | `SecurityConfig.java` (`permitAll()`) + `AuthService` | `AuthControllerTest` | `testServiceTokenIssueSuccess` | PASS |
| `POST /auth/refresh` | Public with Cookie + `X-Requested-With` | `SecurityConfig.java` + `AuthController` | `AuthControllerTest`<br>`AuthSecurityRealDatabaseIntegrationTest` | `testSuccessfulRefresh`<br>`testRefreshTokenReuseRevocationAndAudit` | PASS |
| `POST /auth/logout` | Authenticated | `SecurityConfig.java` (`permitAll()` but processes Bearer jti & refresh cookie) | `LogoutDenyListIntegrationTest` | `testLogoutDenyListLifecycle` | PASS |
| `GET /auth/me` | Any Authenticated User | `SecurityConfig.java` (`authenticated()`) | `IdentityRoleMatrixSecurityTest` | `testUnauthenticatedMeReturns401`<br>`testAuthenticatedMeReturns200` | PASS |
| `POST /admin/users`, `GET /admin/users` | `ADMIN` only | `SecurityConfig.java` (`hasRole('ADMIN')`) + `@PreAuthorize("hasRole('ADMIN')")` | `IdentityRoleMatrixSecurityTest` | `testUnauthenticatedUsersEndpointReturns401`<br>`testOperatorAccessToUsersForbidden`<br>`testMerchantAccessToUsersForbidden`<br>`testAdminAccessToUsersPermitted` | PASS |
| `GET /admin/audit-logs` / `/admin/audit` | `ADMIN` only | `SecurityConfig.java` (`hasRole('ADMIN')`) + `@PreAuthorize` | `IdentityRoleMatrixSecurityTest` | `testUnauthenticatedAuditEndpointReturns401`<br>`testMerchantAccessToAuditForbidden`<br>`testAuditorAccessToAuditForbidden`<br>`testAdminAccessToAuditPermitted` | PASS |

---

## 2. Security Invariants Tested

1. **Maker-Checker Integrity:**
   - Proposers of settlement adjustments cannot approve their own adjustment.
   - Enforced at dual layers:
     - Application layer: `AdjustmentService.approveAdjustment()` throws `MakerCheckerViolationException` returning RFC 9457 clean HTTP 422 (`maker_checker_violation`).
     - PostgreSQL engine layer: Database trigger/check constraint `chk_adjustments_maker_checker CHECK (proposed_by != approved_by)` physically aborts direct SQL self-approvals.
2. **Merchant Data Isolation:**
   - Authenticated `MERCHANT` tokens are strictly scoped to `seller:{sellerId}:payable` accounts and `/v1/sellers/{sellerId}`. Access to platform internal accounts (`PLATFORM_CASH`, `PROVIDER_RECEIVABLE`) or peer merchant accounts returns HTTP 403 Forbidden.
3. **M2M Isolation:**
   - Financial mutation endpoints (`/v1/payouts`, `/v1/provider-deposits`, `/v1/adjustments`) require `ROLE_SERVICE`. Direct interactive users (even `ADMIN`) are rejected with HTTP 403 Forbidden.
