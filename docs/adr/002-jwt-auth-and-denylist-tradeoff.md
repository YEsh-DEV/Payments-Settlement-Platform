# ADR-002: RS256 JWT Design, Dual-Key JWKS Rotation, and Redis Deny-List Fallback

## Status
Accepted

## Context
PayCore separates authorization concerns between an authority service (`identity-service`) and resource servers (`ledger-service`, `settlement-service`). In a microservices architecture, symmetric shared secrets (HS256) leak key material across service boundaries and prevent zero-downtime key rotation. Furthermore, distributed token revocation via Redis introduces availability trade-offs if Redis experiences network partitions or downtime.

## Decision
1. **Asymmetric Cryptography:** Adopt **RS256** (RSA 2048-bit with SHA-256). The private key resides exclusively within `identity-service`. All resource servers validate tokens cryptographically via `identity-service:8081/.well-known/jwks.json`.
2. **Dual-Key Rotation:** The JWKS endpoint publishes both `current` and `previous` public keys (`kid`), enabling seamless 90-day cryptographic key rotations without invalidating active sessions.
3. **Short-Lived Access Tokens:** Access tokens have an expiration of **15 minutes** ($900\text{ seconds}$).
4. **Refresh Token Family Rotation:** Refresh tokens (256-bit entropy stored as SHA-256 hash) rotate on every request. Replay of an already-used token triggers immediate revocation of the entire token family.
5. **Deny-List Availability Trade-Off (Fail-Open):**
   - On logout, the token's `jti` is stored in Redis with a TTL equal to the token's remaining validity.
   - Resource servers check Redis for token revocation on incoming requests.
   - **Trade-off:** If Redis is down or unreachable, the deny-list check is skipped, metric `denylist_check_skipped_total` is incremented, and a warning is logged. The request is permitted if the cryptographic signature and claims are valid. This trade-off is accepted because access tokens have a maximum lifetime of only 15 minutes, prioritizing core ledger availability over instantaneous revocation during cache outages.

## Consequences
- No shared secrets distributed across services.
- Ledger posting and payment captures remain operational even if Redis is temporarily offline.
- Revoked tokens may remain usable for up to 15 minutes during an active Redis outage.
