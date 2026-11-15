# ADR-004: Transactional Outbox Pattern and At-Least-Once Event Delivery Semantics

## Status
Accepted

## Context
When financial state transitions occur in `ledger-service`, downstream consumers in `settlement-service` must be notified to create reconciliation candidate rows. Performing direct dual-writes (committing to PostgreSQL and publishing to Apache Kafka in the same controller method) creates split-brain risks: if Kafka is unavailable or network partitions occur after database commit, downstream events are lost.

## Decision
1. **Transactional Outbox:** State modifications and corresponding domain events are written to the database atomically in the same local transaction.
2. **Two-Phase Asynchronous Outbox Relay:**
   - Worker queries `outbox_events` using `SELECT ... FOR UPDATE SKIP LOCKED` in batches of 100.
   - Dispatches events to Apache Kafka outside the database transaction.
   - Marks rows as published (`published_at = NOW()`) upon receipt of Kafka broker acknowledgement.
3. **Delivery Semantics (At-Least-Once):** Kafka message delivery is guaranteed to be **at-least-once**. Network timeouts during broker ACKs may cause the relay to republish an already-dispatched event.
4. **Consumer Idempotency:** All consumers must be strictly idempotent. Downstream handlers deduplicate incoming events by inserting the event's UUID into `processed_events(event_id)` within the same local transaction that records downstream state (e.g. `settlement_candidates`).
5. **Partition Key Ordering:** Events are keyed in Kafka by their logical aggregate ID (for payment capture and refunds, `paymentId`). This guarantees that a refund event is always assigned to the same partition and consumed strictly after its corresponding payment capture.
6. **Poison Pill Routing:** Unrecoverable deserialization or business processing failures retry 3 times with exponential backoff before being routed to dead-letter queue `ledger.domain-events.dlq`.

## Consequences
- Complete decoupling of ledger transaction throughput from Kafka broker availability.
- Downstream systems must maintain deduplication storage (`processed_events`).
