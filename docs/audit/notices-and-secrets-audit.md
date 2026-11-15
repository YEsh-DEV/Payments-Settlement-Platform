# PayCore: Secrets & Third-Party Notices Audit

**Audit Date:** 2026-11-15T20:42:00+05:30  
**Commit SHA:** `48b365fc4af5a3c435ae98fa9e7c61ad097301bc`  
**Auditor:** Automated Codebase Truth Audit (WP-A)

---

## 1. Git History Secrets Scan

A full regular-expression and keyword scan was executed across all branches and commits in the git history (`git log -p --all`).

### Scan Criteria & Patterns
- RSA/EC/OpenSSH private key blocks: `-----BEGIN [A-Z ]*PRIVATE KEY-----`
- Cloud & SaaS API keys: `AKIA...`, `ghp_...`
- High-entropy API tokens: `api_key = ...`, `secret = ...`, `Bearer ...`
- Known demo strings: `admin123`, `password123`

### Scan Findings
- **High-Risk Secrets:** 0 found. Zero real cloud credentials, zero private keys, and zero external API tokens exist in the repository or git history.
- **Unit Test Mock Secrets:** 1 match found in `services/identity-service/src/test/java/io/github/yesh/paycore/identity/service/AuthServiceTest.java:199`:
  ```java
  String secret = "super-secret-m2m-key";
  ```
  This is a local in-memory string passed to BCrypt encoder during unit testing of service tokens.
- **Development Fallback Passwords in Configs:**
  - `application.yml` and `docker-compose.yml` contain development fallback strings (`identity_dev_password`, `ledger_dev_password`, `settlement_dev_password`).
  - `LoginPage.tsx` contains demo button helpers (`password123`).
  - **Action Required (WP-J):** Enforce that the Spring Boot `prod` profile refuses to start if dev passwords or auto-generated keys are detected. Replace hardcoded demo logins with dynamically seeded credentials.

---

## 2. Third-Party Notices & Attribution Audit

The file `THIRD_PARTY_NOTICES.md` was audited against the codebase for compliance with Section 6 of the Playbook.

### 2.1 Base Repositories Attributed
1. **`Dedmoo/RetailBankingCore`**
   - URL: `https://github.com/Dedmoo/RetailBankingCore.git`
   - Commit: `955c2f7aa83a8df7d30ad1303be5be6ab079e6e8`
   - License: MIT License
   - Mapped Components: Outbox pattern concepts, Redis token bucket structure, and Testcontainers support.
2. **`Gabrielab5/IssuesFlow`**
   - URL: `https://github.com/Gabrielab5/IssuesFlow.git`
   - Commit: `ef4bd84d2847a2569ba249f3906ea1631ef09c0d`
   - License: MIT License
   - Mapped Components: Frontend UI layout structure, daemon scheduling patterns, token deny-list structure.

### 2.2 Leftover Code Scan Results
A case-insensitive recursive grep was performed across all source directories:
```bash
grep -rn -i "retailbanking\|issuesflow\|dedmoo\|gabrielab5" services/ libs/ web/ deploy/ docs/
```
- **Matches:** 0 matches found outside `THIRD_PARTY_NOTICES.md`.
- **Verdict:** PASS. All package names were renamed to `io.github.yesh.paycore.*`. Zero retail banking domain concepts (such as `HouseFundingService` or bank accounts) or IssueFlow ticket models remain in the code.

### 2.3 Discrepancies in `THIRD_PARTY_NOTICES.md`
- The table in `THIRD_PARTY_NOTICES.md` listed files under `services/ledger-service/src/main/java/io/github/yesh/paycore/ledger/messaging/` and `ratelimit/`. In the actual repository, `OutboxEvent.java` is placed in `.../ledger/domain/` and the rate limiter filter was not yet imported into `services/ledger-service`.
- **Action Required (WP-F, WP-J):** When rate limiting and outbox relay refactorings are completed in WP-F and WP-J, verify exact file paths match `THIRD_PARTY_NOTICES.md`.
