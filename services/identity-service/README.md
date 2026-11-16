# Identity Service (`identity-service`)

## 1. Purpose & Responsibilities
`identity-service` provides authentication, role-based authorization, cryptographic key management, and security audit logging for the PayCore platform.

### Key Capabilities:
- **RS256 JWT Token Issuance:** Signs stateless access tokens using RSA 2048-bit key pairs with JWKS endpoint (`/.well-known/jwks.json`).
- **User Management & Authentication:** Supports BCrypt password hashing (cost factor 12) for users with role-based access control.
- **Service-to-Service M2M Authentication:** Authenticates platform microservices via client ID and client secret, issuing scoped machine tokens.
- **Audit Logging:** Immutably writes all authentication and authorization actions to JSONB audit tables.

---

## 2. Configuration Parameters

| Property | Default Value | Description |
|---|---|---|
| `server.port` | `8081` | Service HTTP API port |
| `management.server.port` | `9081` | Actuator and Prometheus metrics port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/identity` | PostgreSQL connection URL |
| `paycore.security.jwt.access-token-validity-seconds` | `900` (15m) | JWT validity duration |
| `paycore.security.jwt.service-token-validity-seconds` | `600` (10m) | M2M machine token validity duration |
| `paycore.security.jwt.key-id` | `key-2026-v1` | Active RSA key identifier |

---

## 3. How It Works (Interview Defense)

1. **User Authentication Flow:**
   - User posts credentials to `POST /auth/login`.
   - `AuthService` verifies BCrypt hash against PostgreSQL `users` table.
   - `JwtTokenService` generates an RS256 JWT containing `sub` (User UUID), `roles`, `sellerId` (if merchant), and expiration.
   - An immutable event is written to `audit_log`.

2. **M2M Service Token Flow:**
   - Platform microservices (such as `settlement-service`) request tokens via `POST /auth/service-token` with client ID and secret.
   - `AuthService` verifies client credentials and issues a machine token with role `SERVICE`.
