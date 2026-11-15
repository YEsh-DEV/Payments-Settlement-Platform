# ADR-001: Platform Versions, Java Runtime, and Code Formatting Standard

## Status
Accepted

## Context
PayCore requires a modern, high-performance, and verifiable back-end foundation for double-entry financial ledger accounting and high-throughput settlement processing. The system must support strict compilation checks, virtual threads where beneficial, modern cryptographic libraries (RS256/Nimbus), and consistent code formatting across all microservices and shared modules.

## Decision
1. **Java Runtime:** Adopt **Java 21 LTS** (`openjdk 21.0.13+9.0.LTS`). Java 21 provides immutable records, pattern matching switch expressions, sequenced collections, and production stability.
2. **Framework Baseline:** Adopt **Spring Boot 3.5.x** with Spring Framework 6.2+.
3. **Build System:** Apache Maven with a root multi-module parent POM managing dependency convergence and plugins.
4. **Code Formatter:** Adopt **Spotless** with `google-java-format` (version 1.24.0).
   - Formatter execution is enforced in CI via `mvn spotless:check`.
   - Developers format locally via `mvn spotless:apply`.
5. **Architectural Governance:** Enforce architecture boundaries and package isolation using **ArchUnit**.

## Consequences
- Guarantees zero formatting debates across pull requests.
- Requires all developers and CI runners to maintain Java 21 LTS.
- Eliminates snapshot dependencies and prevents dependency divergence across submodules.
