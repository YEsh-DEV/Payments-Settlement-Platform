[0;34m[INFO][0m 2026-11-17 00:26:42 Detected direct service ports. Using standalone service routes.

[0;36m================================================================================[0m
[0;36m[STEP 1][0m Health Check & Service Readiness
[0;36m================================================================================[0m
[0;34m[INFO][0m 2026-11-17 00:26:42 Service at http://localhost:9081/actuator/health is UP
[0;34m[INFO][0m 2026-11-17 00:26:42 Service at http://localhost:9082/actuator/health is UP
[0;34m[INFO][0m 2026-11-17 00:26:42 Service at http://localhost:9083/actuator/health is UP
[0;32m[PASS][0m All microservices are healthy and responding.

[0;36m================================================================================[0m
[0;36m[STEP 2][0m User Authentication & JWT Issuance
[0;36m================================================================================[0m
[0;34m[INFO][0m 2026-11-17 00:26:42 Authenticating as admin...
[0;32m[PASS][0m Admin authenticated successfully.
[0;34m[INFO][0m 2026-11-17 00:26:42 Authenticating as analyst...
[0;32m[PASS][0m Analyst authenticated successfully.
[0;34m[INFO][0m 2026-11-17 00:26:42 Verifying token via GET /auth/me...
[0;32m[PASS][0m Token introspection verified: user=admin

[0;36m================================================================================[0m
[0;36m[STEP 3][0m Onboard Sellers & Initialize Merchant Accounts
[0;36m================================================================================[0m
[0;34m[INFO][0m 2026-11-17 00:26:42 Onboarding seller seller_e2e_1_1794855402 (STANDARD tier, 200 bps)...
[0;32m[PASS][0m Seller seller_e2e_1_1794855402 onboarded: account={"id":"54dbea9b-a35a-4fa5-b04a-19acb15371f4","sellerId":"seller_e2e_1_1794855402","name":"Acme Store E2E","feeTier":"STANDARD","feeBps":200,"accountId":"926b5a0e-ab6d-4e57-80ee-bdb1887076ba","accountCode":"MERCHANT_seller_e2e_1_1794855402"}
[0;34m[INFO][0m 2026-11-17 00:26:42 Onboarding seller seller_e2e_2_1794855402 (ENTERPRISE tier, 150 bps)...
[0;32m[PASS][0m Seller seller_e2e_2_1794855402 onboarded.

[0;36m================================================================================[0m
[0;36m[STEP 4][0m Payment Captures, Authorization Hold & Partial Refund
[0;36m================================================================================[0m
[0;34m[INFO][0m 2026-11-17 00:26:43 Capturing payment 1: pay_e2e_101_1794855402 (gross: 200,000 paise)...
[0;32m[PASS][0m Payment 1 captured: gross=200,000, fee=4,000, net=196,000 paise
[0;34m[INFO][0m 2026-11-17 00:26:43 Capturing payment 2: pay_e2e_201_1794855402 (gross: 200,000 paise)...
[0;32m[PASS][0m Payment 2 captured: gross=200,000, fee=3,000, net=197,000 paise
[0;34m[INFO][0m 2026-11-17 00:26:43 Placing authorization hold on seller_e2e_1_1794855402 account (10,000 paise)...
[0;32m[PASS][0m Authorization hold hld_e2e_101_1794855402 is ACTIVE (10,000 paise held)
[0;34m[INFO][0m 2026-11-17 00:26:43 Processing partial refund on payment 1 (20,000 paise)...
[0;32m[PASS][0m Partial refund ref_e2e_101_1794855402 completed.

[0;36m================================================================================[0m
[0;36m[STEP 5][0m Ingest Settlement Candidates
[0;36m================================================================================[0m
[0;34m[INFO][0m 2026-11-17 00:26:43 Registering settlement candidate records for batch processing...
[0;32m[PASS][0m Settlement candidate records registered.

[0;36m================================================================================[0m
[0;36m[STEP 6][0m Settlement Batch Cutoff Execution
[0;36m================================================================================[0m
[0;34m[INFO][0m 2026-11-17 00:26:43 Triggering settlement batch cutoff...
[0;32m[PASS][0m Batch cutoff complete: batchId=BATCH_1794855403199_959368, count=2

[0;36m================================================================================[0m
[0;36m[STEP 7][0m Payout Dispatch & Confirmation
[0;36m================================================================================[0m
[0;34m[INFO][0m 2026-11-17 00:26:43 Generating bank payout file for batch BATCH_1794855403199_959368...
[0;34m[INFO][0m 2026-11-17 00:26:43 Payout file generated. Processing bank confirmation results...
[0;32m[PASS][0m Payout confirmation complete: batch status=COMPLETED

[0;36m================================================================================[0m
[0;36m[STEP 8][0m Provider Settlement File Generation & Ingestion
[0;36m================================================================================[0m
[0;34m[INFO][0m 2026-11-17 00:26:43 Generating provider settlement CSV with simulated discrepancy...
[0;34m[INFO][0m 2026-11-17 00:26:43 Uploading settlement file /tmp/provider_settlement_1794855402.csv...
[0;32m[PASS][0m Settlement file uploaded: status=INGESTED, sha256=4234e92e0dd89e970dc33ed5de697abcee37412d772327e5409f74235db89853

[0;36m================================================================================[0m
[0;36m[STEP 9][0m Reconciliation Engine Execution
[0;36m================================================================================[0m
[0;34m[INFO][0m 2026-11-17 00:26:43 Running multi-stage reconciliation for batch BATCH_1794855403199_959368...
[0;32m[PASS][0m Reconciliation run RUN_b916049a complete: matches=0, cases=4

[0;36m================================================================================[0m
[0;36m[STEP 10][0m Discrepancy Case & Two-Person Adjustment Workflow
[0;36m================================================================================[0m
[0;34m[INFO][0m 2026-11-17 00:26:43 Querying discrepancy cases...
[0;34m[INFO][0m 2026-11-17 00:26:43 Operating on discrepancy case: CASE_bfe1bb85
[0;34m[INFO][0m 2026-11-17 00:26:43 Person 1 (Analyst): Proposing fee adjustment (50 paise)...
[0;32m[PASS][0m Adjustment ADJ_396013ae proposed by analyst.
[0;34m[INFO][0m 2026-11-17 00:26:43 Person 2 (Admin): Approving adjustment ADJ_396013ae (Two-Person Rule)...
[0;32m[PASS][0m Adjustment ADJ_396013ae approved by admin. Status: POSTED. Two-person separation of duties enforced.

[0;36m================================================================================[0m
[0;36m[STEP 11][0m Provider Deposit Recording
[0;36m================================================================================[0m
[0;34m[INFO][0m 2026-11-17 00:26:43 Recording provider net settlement deposit...
[0;32m[PASS][0m Provider deposit dep_e2e_001_1794855402 recorded: journal=d1830bbe-6bc8-4e38-8ccf-c0ede490ea4d

[0;36m================================================================================[0m
[0;36m[STEP 12][0m Continuous Ledger Integrity Audit
[0;36m================================================================================[0m
[0;34m[INFO][0m 2026-11-17 00:26:43 Triggering continuous ledger integrity audit: GET /v1/integrity...
{
  "healthy": true,
  "activeStrategy": "S0_PESSIMISTIC",
  "checkedAccountsCount": 605,
  "checkedJournalsCount": 116404,
  "totalDebitsPaise": 59165487100,
  "totalCreditsPaise": 59165487100,
  "deltaPaise": 0,
  "violations": [],
  "checkedAt": "2026-11-16T18:56:47.891808134Z"
}
[0;32m[PASS][0m Ledger integrity check PASSED cleanly with 0 violations! All invariants I1-I8 hold.

[0;32m================================================================================[0m
[0;32mPAYCORE END-TO-END BUSINESS FLOW COMPLETED SUCCESSFULLY[0m
[0;32m================================================================================[0m
Summary of verified capabilities:
  [x] Microservice Health & Routing (Gateway & Direct)
  [x] Identity Authentication, JWT Token Issuance & Scopes
  [x] Seller Onboarding & Account Provisioning (Idempotent)
  [x] Payment Capture with Real-Time Fee Calculation
  [x] Active Authorization Hold Placement & Balance Locking
  [x] Partial Refund with Debit/Credit Symmetry
  [x] Automated Settlement Batch Cutoff with Advisory Locks
  [x] Payout Dispatch & Bank Confirmation Processing
  [x] Provider Settlement File Streaming Upload & Deduplication
  [x] Multi-Stage Reconciliation & Discrepancy Case Creation
  [x] Dual-Control / Two-Person Separation of Duties for Adjustments
  [x] Provider Deposit Posting
  [x] Zero Invariant Violations on Continuous Ledger Integrity Audit
================================================================================
