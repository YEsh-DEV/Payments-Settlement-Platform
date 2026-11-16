-- V5__idempotency_status_code.sql: Store response HTTP status code for exact replay fidelity

ALTER TABLE idempotency_keys ADD COLUMN response_status_code INTEGER DEFAULT 201;
