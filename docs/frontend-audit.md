# PayCore Frontend Read-Only Audit & Discrepancy Report (WP-L Preparation)

**Audit Date:** 2026-11-17T07:55:00+05:30  
**Target Codebase:** `web/ops-console/` (React 19 + TypeScript + Vite + Tailwind CSS + Lucide Icons)  
**Methodology:** Strict read-only static code analysis without executing builds, runtime servers, or Playwright tests.

---

## 1. Executive Summary & Architectural Findings

A thorough, file-by-file audit was conducted across all 11 pages, UI components, API clients, contexts, and routing definitions in `web/ops-console`. 

### Key Findings:
1. **Zero Storage Leaks:** Neither `localStorage` nor `sessionStorage` is utilized anywhere in the frontend codebase. Access tokens reside strictly in-memory (`web/ops-console/src/api/client.ts`), and refresh tokens are managed exclusively via `HttpOnly; SameSite=Strict` browser cookies.
2. **Reverse Proxy URL Routing Discrepancies:** A major mismatch exists between Nginx proxy routing (`deploy/nginx.conf`) and Axios API requests (`web/ops-console/src/api/paycoreApi.ts`). While Nginx routes by service sub-path (`/api/identity/`, `/api/ledger/`, `/api/settlement/`), the frontend client requests unqualified `/api/*` endpoints (e.g. `/api/cases`, `/api/recon/runs`), which fail to resolve to backend services and fall through to SPA `index.html`.
3. **Missing Backend Endpoints Leading to Mock Data Fallbacks:**
   - `GET /admin/audit`: Called by `AuditLogsPage.tsx`, but no REST controller exposes this endpoint in `identity-service`, causing the page to display hardcoded fallback audit records.
   - `GET /admin/users`: Called by `UsersPage.tsx`, where network failures trigger an in-memory fallback array of 4 demo users.
   - `DashboardPage.tsx`: Falls back to a hardcoded `'99.4'` match rate when reconciliation runs have not yet been executed.
   - `MerchantPortalPage.tsx`: Queries `MERCHANT_{sellerId}`, whereas `ledger-service` models seller accounts as `seller:{sellerId}:payable`.

---

## 2. Comprehensive Page-by-Page Audit Matrix

| Page Component | Intended Roles | API Endpoints Called | Backend Service Target | Observed Mock / Hard-Coded Fallbacks | State Handling (Loading, Empty, Error) |
|:---|:---:|:---|:---:|:---|:---|
| **LoginPage.tsx** | Public | `POST /auth/login` | `identity-service:8081` | None. Form handles input validation; displays red banner on 401/503. | Loading spinner on button; comprehensive error banners. |
| **DashboardPage.tsx** | `ADMIN`, `ANALYST`, `AUDITOR` | `GET /cases`<br>`GET /batches`<br>`GET /recon/runs` | `settlement-service:8083` | **Hard-coded Fallback:** If `reconRuns` is empty, displays static `'99.4'` match rate (line 67). | Skeleton loaders for summary cards; Recharts bar chart handles empty array. |
| **LedgerPage.tsx** | `ADMIN`, `ANALYST`, `AUDITOR` | `GET /accounts/{code}`<br>`GET /accounts/{code}/statement` | `ledger-service:8082` | None. Queries live account balances and journal legs. | Loading spinner on search; empty state displayed when statement has 0 rows. |
| **CasesPage.tsx** | `ADMIN`, `ANALYST` | `GET /cases` | `settlement-service:8083` | None. Renders filterable discrepancy table with SLA badges. | Table loading state; "No cases found" empty state; error toast. |
| **CaseDetailPage.tsx** | `ADMIN`, `ANALYST` | `GET /cases/{id}`<br>`POST /cases/{id}/assign`<br>`POST /cases/{id}/transition`<br>`GET /cases/{id}/notes`<br>`POST /cases/{id}/notes`<br>`POST /adjustments/propose` | `settlement-service:8083` | None. Maker-checker dual control buttons for proposing adjustments. | Full-page spinner during fetch; disabled buttons during mutations; optimistic note addition. |
| **ReconciliationPage.tsx**| `ADMIN`, `ANALYST` | `GET /recon/runs`<br>`GET /recon/runs/{id}/matches`<br>`POST /recon/runs`<br>`POST /files/upload` | `settlement-service:8083` | None. 4-stage breakdown UI. | Status alert on file upload; loading indicators for runs list. |
| **PayoutsPage.tsx** | `ADMIN`, `ANALYST` | `GET /batches`<br>`GET /batches/{id}/records`<br>`POST /batches/run` | `settlement-service:8083` | None. Cutoff batch table and record breakdown. | Button disabled with "Executing Cutoff..." during mutation; empty state tables. |
| **AuditLogsPage.tsx** | `ADMIN`, `AUDITOR` | `GET /admin/audit` | `identity-service:8081` (Endpoint Missing) | **Deterministic Mock Fallback:** On API error, catch block populates 4 hardcoded fake audit records (`aud-001` through `aud-004`). | Search and resource filter bars; expandable JSON viewer. |
| **UsersPage.tsx** | `ADMIN` | `GET /admin/users`<br>`POST /admin/users` | `identity-service:8081` | **Mock Fallback:** On API error, catch block populates 4 fake users (`admin`, `analyst`, `auditor`, `seller_flipkart`). | Add user dialog; form validation; table loading state. |
| **MerchantPortalPage.tsx**| `MERCHANT` | `GET /accounts/{code}`<br>`GET /accounts/{code}/statement` | `ledger-service:8082` | **Account Code Mismatch:** Queries `MERCHANT_{username}` instead of `seller:{sellerId}:payable`. | Summary cards show 0.00 on missing account; statement table shows empty state. |
| **SystemPage.tsx** | All authenticated | None (Static Display) | None | **Static UI:** Displays hardcoded I1-I8 invariants and static "50-Iteration SIGKILL Crash Harness: 100% Passed" banner without calling `/v1/integrity`. | Pure static rendering. |

---

## 3. Storage & Security Audit

### 3.1 Token Storage Audit
```typescript
// web/ops-console/src/api/client.ts:6-17
// Access token strictly held in-memory (never written to localStorage)
let inMemoryAccessToken: string | null = null;
export const getToken = (): string | null => inMemoryAccessToken;
export const setToken = (token: string | null): void => { inMemoryAccessToken = token; };
export const clearToken = (): void => { inMemoryAccessToken = null; };
```
- **Finding:** Verified **ZERO** instances of `localStorage` or `sessionStorage` in `web/ops-console/src/`.
- **Verdict:** **PASS (SEC-01 / OWASP Compliant).** Completely immune to localStorage credential theft via XSS.

### 3.2 Role-Based Routing Audit (`ProtectedRoute.tsx`)
```typescript
// web/ops-console/src/components/ProtectedRoute.tsx
export function ProtectedRoute({ allowedRoles }: { allowedRoles?: Role[] }) {
  const { user, isAuthenticated, isLoading } = useAuth();
  if (isLoading) return <LoadingScreen />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (allowedRoles && !allowedRoles.some(r => user?.roles.includes(r))) {
    return <Navigate to="/unauthorized" replace />;
  }
  return <Outlet />;
}
```
- **Finding:** All operational pages are protected by `ProtectedRoute`. Unauthenticated visitors are redirected to `/login`.
- **Role Isolation:**
  - `UsersPage.tsx` restricted to `['ADMIN']`.
  - `MerchantPortalPage.tsx` restricted to `['MERCHANT']`.
  - `AuditLogsPage.tsx` restricted to `['ADMIN', 'AUDITOR']`.
  - `ReconciliationPage.tsx`, `CasesPage.tsx`, `PayoutsPage.tsx` restricted to `['ADMIN', 'ANALYST']`.

---

## 4. API & Nginx Routing Gap Analysis

### 4.1 The URL Mapping Problem
In `deploy/nginx.conf`:
- `/api/identity/` proxies to `identity-service:8081`
- `/api/ledger/` proxies to `ledger-service:8082`
- `/api/settlement/` proxies to `settlement-service:8083`

In `web/ops-console/src/api/paycoreApi.ts`:
- Requests are made to `/auth/login`, `/cases`, `/recon/runs`, `/batches`, `/accounts/{code}`, etc.
- With `VITE_API_BASE_URL=/api`, requests resolve to `/api/cases`, which does not match `/api/settlement/`.
- Nginx matches `location /` and returns the SPA `index.html` with HTTP 200, causing JSON parsing errors in the browser.

### 4.2 Endpoint Path Discrepancies
| Frontend Call in `paycoreApi.ts` | Backend Controller Definition | Required Frontend or Nginx Path |
|:---|:---|:---|
| `/auth/login` | `AuthController`: `@PostMapping("/auth/login")` | `/api/identity/auth/login` |
| `/cases` | `CaseController`: `@RequestMapping("/v1/cases")` | `/api/settlement/v1/cases` |
| `/adjustments/propose` | `AdjustmentController`: `@RequestMapping("/v1/adjustments")` | `/api/settlement/v1/adjustments/propose` |
| `/recon/runs` | `ReconciliationController`: `@RequestMapping("/v1/recon")` | `/api/settlement/v1/recon/runs` |
| `/files/upload` | `SettlementFileController`: `@RequestMapping("/v1/files")` | `/api/settlement/v1/files/upload` |
| `/batches/run` | `BatchController`: `@PostMapping("/cutoff")` | `/api/settlement/v1/batches/cutoff` |
| `/accounts/{code}` | `AccountController`: `@RequestMapping("/v1/accounts")` | `/api/ledger/v1/accounts/{code}` |
| `/admin/audit` | **MISSING IN BACKEND** | Needs `AuditLogController` in `identity-service` |

---

## 5. Remediation Plan for WP-L (Frontend Hardening)

When the owner grants approval to begin the frontend phase (WP-L):
1. **Nginx & Client Alignment:** Align `deploy/nginx.conf` and `paycoreApi.ts` so that all requests route to their correct microservices and preserve the `/v1/` prefix.
2. **Implement Missing Backend Audit Endpoint:** Add `@GetMapping("/admin/audit")` to `UserAdminController.java` or create `AuditLogController.java` in `identity-service` backed by `AuditLogRepository`, eliminating the fake mock fallback in `AuditLogsPage.tsx`.
3. **Purge All Mock Fallbacks:** Remove catch-block mock arrays in `UsersPage.tsx` and `AuditLogsPage.tsx`, replacing them with authentic error boundaries and toast notifications.
4. **Fix Merchant Account Formulation:** Update `MerchantPortalPage.tsx` to query `seller:{sellerId}:payable` matching ledger service conventions.
5. **Connect SystemPage to Live Invariants:** Update `SystemPage.tsx` to poll `GET /v1/integrity` and display real-time mathematical integrity counters.
