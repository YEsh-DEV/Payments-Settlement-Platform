# PayCore Deployment & Sizing Guide

## 1. Overview
PayCore is architected as high-throughput, event-driven financial infrastructure composed of modular Spring Boot services backed by PostgreSQL 16 (partitioned & striped ledger) and Redis (caching and striped locking).

This document details hardware requirements, measured runtime memory footprints, container configuration, reverse-proxy setup, and operational deployment workflows.

---

## 2. Measured Resource Footprint (Live System Evidence)

Memory measurements taken from live running JVM instances and database backends during full end-to-end load execution (`scripts/e2e-flow.sh`):

| Component | Runtime / Base | Measured RSS (Unconstrained) | Docker Container Limit | Recommended JVM `-Xmx` |
|---|---|---|---|---|
| **Identity Service** | Eclipse Temurin 21 JRE | 441 MB | 512 MB | `-Xms128m -Xmx384m` |
| **Ledger Service** | Eclipse Temurin 21 JRE | 1,223 MB | 768 MB | `-Xms256m -Xmx512m` |
| **Settlement Service** | Eclipse Temurin 21 JRE | 456 MB | 768 MB | `-Xms256m -Xmx512m` |
| **PostgreSQL 16** | Debian Linux 64-bit | ~350 MB (buffer pool + connections) | 512 MB | `shared_buffers = 128MB` |
| **Redis 8.10** | In-Memory K/V Store | 5.5 MB | 256 MB | `maxmemory 128mb` |
| **Ops Console / Reverse Proxy** | Nginx Alpine (Unprivileged) | 15 MB | 128 MB | N/A |
| **Total Stack Active Footprint** | | **~2.5 GB** | **~2.9 GB** | |

### Recommended Hardware Sizing
- **Minimum VM (Development / Recruiter Demo):**
  - **vCPU:** 2 vCPUs
  - **RAM:** 4 GB RAM (e.g. AWS `t3.medium`, DigitalOcean 4GB Droplet, or local laptop)
  - **Disk:** 20 GB SSD (NVMe preferred for PostgreSQL WAL write throughput)
- **Production VM (Isolated Core Profile):**
  - **vCPU:** 4-8 vCPUs
  - **RAM:** 8 GB - 16 GB RAM (e.g. AWS `c6i.xlarge` or `c6i.2xlarge`)
  - **Disk:** 100 GB+ provisioned IOPS SSD (`gp3` with 3,000 IOPS minimum)

---

## 3. Deployment Topology & Compose Profiles

PayCore provides Docker Compose topologies via root `docker-compose.yml` and `deploy/docker-compose.yml`.

### Profile Definitions:
1. **`core` Profile (Minimal Viable Core):**
   - Runs PostgreSQL 16, Redis, `identity-service`, `ledger-service`, and `settlement-service`.
   - Bypasses Kafka broker overhead for direct synchronous HTTP/REST candidate ingestion and outbox polling.
   - Command:
     ```bash
     docker compose --profile core up --build -d
     ```
2. **`full` Profile (Enterprise Production):**
   - Includes Apache Kafka 3.7 with KRaft mode and Ops Console (Nginx).
   - Command:
     ```bash
     docker compose --profile full up --build -d
     ```

---

## 4. Container Hardening & Security Standards

Every production container in PayCore adheres to non-negotiable security guardrails:
1. **Multi-Stage Builds:** Multi-stage `eclipse-temurin:21-jdk-jammy` build container -> `eclipse-temurin:21-jre-jammy` minimal runtime image (zero Maven/compiler tools in final container).
2. **Non-Root Execution:** All services run under dedicated unprivileged system users (`paycore:10001` or `nginx:101`). Root logins and privilege escalations are forbidden.
3. **Container Health Checks:**
   - Database: `pg_isready -U postgres`
   - Cache: `redis-cli ping`
   - Microservices: HTTP GET `/actuator/health` on dedicated management ports (`9081`, `9082`, `9083`).
4. **Memory Hardening:** Each service defines strict hard memory limits (`mem_limit`) and reservations (`mem_reservation`) to prevent out-of-memory cascading kills.

---

## 5. Reverse Proxy Configuration (Caddy / Nginx)

When deploying to a single VM, Nginx or Caddy acts as the unified API Gateway:

### Nginx Routing Configuration (`web/ops-console/nginx.conf`):
```nginx
# API Gateway Routing
location /api/auth/ {
    proxy_pass http://identity-service:8081/auth/;
}

location /api/identity/ {
    proxy_pass http://identity-service:8081/;
}

location /api/ledger/ {
    proxy_pass http://ledger-service:8082/;
}

location /api/settlement/ {
    proxy_pass http://settlement-service:8083/;
}
```

### Security Headers Enforced:
- `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none';`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`

---

## 6. Zero-Downtime Seeding & Verification

### Step 1: Database Initialization & Seeding
```bash
# Seed demo credentials or secure random passwords
./scripts/seed --demo
```
Output verifies:
- `admin` (Role `ADMIN`)
- `analyst` (Role `ANALYST`)
- `operator` (Role `OPERATOR`)
- `merchant_acme` (Role `MERCHANT`)
- 5 base system double-entry accounts (`BANK_CASH`, `PROVIDER_RECEIVABLE`, `PAYOUT_IN_TRANSIT`, `PLATFORM_FEE_REVENUE`, `SELLER_PAYABLE`).

### Step 2: Full End-to-End Execution
```bash
./scripts/e2e-flow.sh
```
Executes all 12 stages:
1. Health & Readiness probes across microservices
2. RS256 JWT Issuance & Introspection (`/auth/me`)
3. Seller Onboarding (`STANDARD` 200 bps & `ENTERPRISE` 150 bps)
4. Payment Captures, Authorization Hold & Partial Refund
5. Candidate Ingestion for Settlement
6. Batch Cutoff Execution with PostgreSQL Advisory Locking
7. Payout Dispatch & Bank Confirmation (`COMPLETED`)
8. Provider Settlement File Streaming Upload (SHA-256 deduplicated)
9. Multi-Stage Reconciliation & Case Detection
10. Dual-Control Maker-Checker Two-Person Operational Adjustment
11. Provider Net Settlement Deposit Posting
12. Continuous Ledger Integrity Audit (Verifies $I1$ through $I8$ with 0 violations)
