# ADR-010: Fail-Closed Rate Limiting for Mutation Endpoints During Redis Outages

## Status
Accepted

## Context
PayCore uses a distributed Token Bucket rate limiter (`RedisRateLimiterFilter`) backed by Redis to protect the ledger, settlement, and identity services from denial-of-service (DoS) attacks, brute-force credential stuffing, and thundering herd spikes. When Redis experiences high latency, network partitions, or outages, an architectural decision must be made regarding request triage: fail open (permit requests without rate limiting) or fail closed (reject requests with HTTP 503).

In financial ledger systems, allowing unmetered write traffic during infrastructure degradation risks database thread pool starvation, connection exhaustion, lock contention, and evasion of login rate limits. Conversely, blocking read-only queries during a cache outage unnecessarily degrades platform observability and read availability.

## Decision
1. **Asymmetric Failure Policy:**
   - **Read Operations Bypass (Fail-Open):** HTTP `GET`, `HEAD`, and `OPTIONS` requests completely bypass the Redis rate limiter. During a Redis outage, all read traffic continues unaffected.
   - **Health & Cryptographic Endpoints Bypass:** Health checks (`/actuator/**`) and public JWKS keys (`/.well-known/**`) bypass rate limiting, ensuring continuous service mesh telemetry and token verification.
   - **Write & Mutation Operations Fail-Closed:** All HTTP mutation methods (`POST`, `PUT`, `DELETE`, `PATCH`), including `/auth/login`, `/auth/service-token`, `/v1/payments`, `/v1/payouts`, and `/v1/batches/cutoff`, fail closed when Redis is unreachable or returns an error.
2. **Error Contract:**
   - Rejected mutation requests return HTTP `503 Service Unavailable` with `ProblemDetails`:
     - Code: `rate_limiter_unavailable`
     - Detail: `"Rate limiter cache unavailable; write operations fail closed for safety"`
3. **Operational Consequence:**
   - **Impact on Authentication:** Because `/auth/login` is an HTTP `POST` mutation, no user can log in during an active Redis outage. Existing authenticated sessions remain valid until access token expiration (15 minutes).
   - **Impact on Payments:** Payment captures and payout dispatches cannot be created until Redis connectivity is restored.
   - **Safety Benefit:** Protects the underlying PostgreSQL database from unchecked concurrency spikes and ensures brute-force login defenses cannot be bypassed by intentionally degrading Redis.

## Verification
Automated test `testReadsAndJwksWorkWhenRedisIsDown` in `libs/paycore-common` verifies that:
1. `GET /v1/sellers/1` succeeds when Redis is down.
2. `GET /.well-known/jwks.json` succeeds when Redis is down.
3. `POST /v1/payments` receives HTTP 503 when Redis is down.
